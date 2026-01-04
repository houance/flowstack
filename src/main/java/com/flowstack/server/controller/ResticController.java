package com.flowstack.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flowstack.server.core.engine.FlowEngine;
import com.flowstack.server.core.model.definition.FlowDefinition;
import com.flowstack.server.core.model.definition.FlowNode;
import com.flowstack.server.core.model.definition.ParamValue;
import com.flowstack.server.core.model.execution.FlowContext;
import com.flowstack.server.enums.DeletedEnum;
import com.flowstack.server.exception.BusinessException;
import com.flowstack.server.exception.ValidationException;
import com.flowstack.server.mapper.SnapshotMetaMapper;
import com.flowstack.server.model.SystemSettings;
import com.flowstack.server.model.api.global.FlowResponse;
import com.flowstack.server.model.api.snapshot.RestoreRequest;
import com.flowstack.server.model.api.snapshot.SnapshotItemDTO;
import com.flowstack.server.model.db.SnapshotMetaEntity;
import com.flowstack.server.node.registry.FieldRegistry;
import com.flowstack.server.node.restic.model.SnapshotNode;
import com.flowstack.server.util.FilesystemUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@RestController
@Slf4j
@CrossOrigin(originPatterns = "*")
@RequestMapping("/restic")
@RequiredArgsConstructor
public class ResticController {
    private final FlowEngine flowEngine;
    private final SystemSettings systemSettings;
    private final SnapshotMetaMapper snapshotMetaMapper;
    private final ConcurrentHashMap<String, Future<FlowContext>> downloadJobResultMap = new ConcurrentHashMap<>();
    private final TaskScheduler generalTaskScheduler;

    @GetMapping("/get-all-snapshots")
    public FlowResponse<List<SnapshotMetaEntity>> getAllSnapshots() {
        LambdaQueryWrapper<SnapshotMetaEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SnapshotMetaEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        queryWrapper.isNotNull(SnapshotMetaEntity::getSnapshotId);
        return FlowResponse.success(this.snapshotMetaMapper.selectList(queryWrapper));
    }

    @PostMapping("/get-snapshot-items")
    public FlowResponse<List<SnapshotItemDTO>> getSnapshotItems(
            @RequestBody SnapshotMetaEntity snapshotMetaEntity,
            @RequestParam(value = "filter", defaultValue = "/") String filter) {
        // 归一化 filter, 去掉尾部一个或多个斜杠
        String finalFilter = filter.equals("/") ? filter : filter.replaceAll("/+$", "");
        // 执行 ls
        FlowDefinition tempFlow = buildLsFlow(snapshotMetaEntity, filter);
        FlowContext context;
        try {
            context = this.flowEngine.executeOnce(tempFlow).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new BusinessException("ls 执行失败", e);
        }
        List<SnapshotNode> snapshotNodes = FieldRegistry.getValue(FieldRegistry.RESTIC_SNAPSHOT_NODES, context);
        return FlowResponse.success(snapshotNodes.stream()
                .filter(n -> !n.getPath().equals(finalFilter)) // 去掉 filter 本身, 也就是当前文件夹
                .map(n -> new SnapshotItemDTO(
                        n.getName(),
                        n.getType(),
                        n.getPath(),
                        n.isDirectory() ? 0L : n.getSize().longValue(),
                        n.getCtime().toInstant()))
                .toList());
    }

    private FlowDefinition buildLsFlow(SnapshotMetaEntity snapshotMetaEntity, String filter) {
        return new FlowDefinition("restic-ls-temp", List.of(
                new FlowNode(
                        "1",
                        "fetch_snapshot_nodes",
                        Map.of(
                                FieldRegistry.RESTIC_SNAPSHOT_ID, new ParamValue(snapshotMetaEntity.getSnapshotId()),
                                FieldRegistry.RESTIC_LS_FILTER, new ParamValue(filter),
                                FieldRegistry.RESTIC_PASSWORD,
                                new ParamValue(systemSettings.getRestic().getBackupPassword()),
                                FieldRegistry.RESTIC_BACKUP_REPOSITORY,
                                new ParamValue(snapshotMetaEntity.getBackupRepository())
                        ),
                        List.of()
                )
        ));
    }

    @PostMapping("/submit-download-job")
    public FlowResponse<String> submitDownloadJob(@RequestBody RestoreRequest restoreRequest) {
        if (ObjectUtils.anyNull(
                restoreRequest,
                restoreRequest.getSnapshotMetaEntity(),
                restoreRequest.getSnapshotItemDTOList())) {
            throw new ValidationException("restore request, snapshotMetaEntity 或 snapshotItemDTOList is null");
        }
        // 将 item 转换为 nodes
        List<SnapshotNode> snapshotNodes = restoreRequest.getSnapshotItemDTOList().stream()
                .map(n -> new SnapshotNode()
                        .setName(n.getName())
                        .setPath(n.getPath())
                        .setType(n.getType()))
                .toList();
        // 生成唯一 ID
        String uuid = UUID.randomUUID().toString();
        // 提交 restore 任务
        SnapshotMetaEntity snapshotMetaEntity = restoreRequest.getSnapshotMetaEntity();
        FlowDefinition restoreFlow = new FlowDefinition("restic-ls-temp", List.of(
                new FlowNode(
                        "1",
                        "restore",
                        Map.of(
                                FieldRegistry.RESTIC_SNAPSHOT_ID, new ParamValue(snapshotMetaEntity.getSnapshotId()),
                                FieldRegistry.RESTIC_SNAPSHOT_NODES, new ParamValue(snapshotNodes),
                                FieldRegistry.RESTIC_PASSWORD,
                                new ParamValue(systemSettings.getRestic().getBackupPassword()),
                                FieldRegistry.RESTIC_BACKUP_REPOSITORY,
                                new ParamValue(snapshotMetaEntity.getBackupRepository())
                        ),
                        List.of()
                )
        ));
        Future<FlowContext> task = this.flowEngine.executeOnce(restoreFlow);
        this.downloadJobResultMap.put(uuid, task);
        // 返回 uuid
        return FlowResponse.success(uuid);
    }

    @PostMapping("/get-download-result")
    public ResponseEntity<Object> getDownloadResult(
            @RequestParam("jobId") String jobId,
            @RequestParam("isPreview") Boolean isPreview) {
        if (!downloadJobResultMap.containsKey(jobId)) {
            return ResponseEntity
                    .status(HttpStatus.GONE)
                    .body("restore file is delete");
        }
        Future<FlowContext> task = downloadJobResultMap.get(jobId);
        if (!task.isDone()) {
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .header("Retry-After", "5")
                    .body("not complete");
        }
        FlowContext context;
        try {
            context = task.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            // 删除 task
            this.downloadJobResultMap.remove(jobId);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("server side error. ex %s".formatted(e.getMessage()));
        }
        Path file = FieldRegistry.getValue(FieldRegistry.RESTIC_RESTORE_RESULT, context);
        UrlResource urlResource;
        try {
            urlResource = new UrlResource(file.toUri());
        } catch (MalformedURLException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("path can't convert to url. ex is %s").formatted(e.getMessage()));
        }
        String fileName = file.getFileName().toString();
        // 不是 preview 则直接返回文件
        if (!isPreview) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(urlResource);
        }
        // 是预览则判断 media type 并返回二进制流
        MediaType mediaType = determineContentType(fileName);
        if (ObjectUtils.isEmpty(mediaType)) {
            return ResponseEntity
                    .status(HttpStatus.NOT_IMPLEMENTED)
                    .body("fileType not supported.");
        }
        // 30 分钟后删除 task, 删除 file 的目录
        this.generalTaskScheduler.schedule(
                () -> {
                    this.downloadJobResultMap.remove(jobId);
                    FilesystemUtil.deleteFileParentDir(file);
                },
                Instant.now().plus(30, ChronoUnit.MINUTES)
        );
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(urlResource);
    }

    private MediaType determineContentType(String filename) {
        // 根据文件扩展名返回对应的 MIME 类型
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> MediaType.APPLICATION_JSON;
            case "txt" -> MediaType.parseMediaType("text/plain; charset=UTF-8"); // 明确指定字符集，避免中文乱码
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            // ... 可以添加更多支持的类型
            default -> null;
        };
    }

    @PostConstruct
    protected void clearTempDir() {
        FilesystemUtil.clearTempDirWithPrefix("restore");
    }
}
