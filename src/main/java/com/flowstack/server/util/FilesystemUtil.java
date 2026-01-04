package com.flowstack.server.util;

import com.flowstack.server.exception.BusinessException;
import com.flowstack.server.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
public class FilesystemUtil {
    private static final RandomStringUtils RANDOM_STRING_UTILS = RandomStringUtils.insecure();

    public static void clearTempDirWithPrefix(String prefix) {
        List<Path> dirStartWithPrefix;
        try (Stream<Path> list = Files.list(FileUtils.getTempDirectory().toPath())) {
            dirStartWithPrefix = list
                    .filter(path -> (Files.isDirectory(path) && path.getFileName().toString().startsWith(prefix)))
                    .toList();
        } catch (IOException e) {
            throw new BusinessException("获取临时目录下所有 %s 开头的失败".formatted(prefix), e);
        }
        if (CollectionUtils.isEmpty(dirStartWithPrefix)) {
            return;
        }
        for (Path dir : dirStartWithPrefix) {
            try {
                FileUtils.deleteDirectory(dir.toFile());
            } catch (IOException e) {
                throw new BusinessException("删除目录 %s 失败".formatted(dir.toString()), e);
            }
        }
    }

    public static void deleteFileParentDir(Path file) {
        if (ObjectUtils.isEmpty(file) || !Files.exists(file)) {
            return;
        }
        try {
            FileUtils.deleteDirectory(file.getParent().toFile());
        } catch (IOException e) {
            throw new BusinessException("删除文件 %s 的目录失败".formatted(file.getParent().toString()), e);
        }
    }

    public static Path isFolderPathValid(
            String folderPathString) throws ValidationException, BusinessException {
        if (StringUtils.isBlank(folderPathString)) {
            throw new ValidationException("isFolderPathValid failed. folderPathString is null");
        }
        Path folder = Paths.get(folderPathString);
        if (Files.exists(folder) && Files.isDirectory(folder)) {
            return folder;
        } else {
            throw new BusinessException(("isFolderPathValid failed. " +
                    "folderPathString doesn't exist or is not folder." +
                    "folderPathString is %s").formatted(folderPathString));
        }
    }

    // 获取 folderPathString 下所有的文件, 并压缩到同样路径下的 zip file
    public static Path zipAllFile(String folderPathString, String prefix)
            throws ValidationException, BusinessException {
        // 检查参数
        Path folder = isFolderPathValid(folderPathString);
        // 使用 RandomStringUtils 生成随机文件名
        String randomName = RANDOM_STRING_UTILS.nextAlphanumeric(9);
        String zipFileName = String.format("%s-%s.zip", prefix, randomName);
        // 创建 zip file
        Path zipFile = folder.resolve(zipFileName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            // 使用 FileUtils 获取所有文件
            Collection<File> files = FileUtils.listFiles(
                    folder.toFile(),
                    null,  // 所有文件类型
                    true   // 递归获取
            );
            for (File file : files) {
                // 跳过ZIP文件自身
                if (file.toPath().equals(zipFile)) {
                    continue;
                }
                // 计算相对路径
                String relativePath = folder.relativize(file.toPath())
                        .toString()
                        .replace("\\", "/");
                // 创建ZIP条目
                ZipEntry zipEntry = new ZipEntry(relativePath);
                zos.putNextEntry(zipEntry);
                // 复制文件到 zip file 中
                try (FileInputStream fis = new FileInputStream(file)) {
                    IOUtils.copy(fis, zos);
                }
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new BusinessException("zipAllFile failed.", e);
        }
        return zipFile;
    }
}
