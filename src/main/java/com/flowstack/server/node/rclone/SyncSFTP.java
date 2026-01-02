package com.flowstack.server.node.rclone;

import com.flowstack.server.core.annotaion.Node;
import com.flowstack.server.core.model.base.BaseNode;
import com.flowstack.server.core.model.execution.FlowContext;
import com.flowstack.server.core.model.execution.NodeResult;
import com.flowstack.server.exception.BusinessException;
import com.flowstack.server.node.model.CommandResult;
import com.flowstack.server.node.rclone.utils.RcloneUtil;
import com.flowstack.server.node.registry.FieldRegistry;
import com.flowstack.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.lang3.StringUtils;

@Node(
        name = "sync_sftp",
        description = "使用 rclone sync + sftp 后端, 同步文件夹",
        group = "rclone",
        inputParams = {
                FieldRegistry.SOURCE_DIRECTORY,
                FieldRegistry.RCLONE_SFTP_CONNECTION,
                FieldRegistry.DST_DIRECTORY
        }
)
@Slf4j
public class SyncSFTP extends BaseNode {
    @Override
    public NodeResult execute(FlowContext context) {
        String srcDir = FieldRegistry.getString(FieldRegistry.SOURCE_DIRECTORY, context);
        String sftpConnection = FieldRegistry.getString(FieldRegistry.RCLONE_SFTP_CONNECTION, context);
        String dstDir = FieldRegistry.getString(FieldRegistry.DST_DIRECTORY, context);
        if (StringUtils.isAnyBlank(srcDir, sftpConnection, dstDir)) {
            return NodeResult.failed("srcDir, sftpConnection, dstDir is blank");
        }
        FilesystemUtil.isFolderPathValid(srcDir);
        if (!dstDir.startsWith("/")) {
            return NodeResult.failed("dst dir 不是以 / 开头");
        }
        String[] connectionInfo = sftpConnection.split(";");
        if (connectionInfo.length != 3) {
            return NodeResult.failed("sftp connection 不正确, length = %s".formatted(connectionInfo.length));
        }
        // build command line
        CommandLine commandLine = new CommandLine("rclone");
        commandLine.addArgument("sync");
        commandLine.addArgument(srcDir);
        commandLine.addArgument(":sftp,host=%s,user=%s,pass=%s:%s".formatted(
                connectionInfo[0],
                connectionInfo[1],
                this.obscured(connectionInfo[2]),
                dstDir)
        );
        // 命令执行
        CommandResult commandResult = RcloneUtil.execute(commandLine, true);
        return commandResult.isSuccess() ?
                NodeResult.success() :
                NodeResult.failed(commandResult.getError());
    }

    private String obscured(String password) {
        CommandLine commandLine = new CommandLine("rclone");
        commandLine.addArgument("obscure");
        commandLine.addArgument(password);
        CommandResult commandResult = RcloneUtil.execute(commandLine, false);
        if (!commandResult.isSuccess()) {
            throw new BusinessException(("混淆 password %s 失败. " +
                    "error 是 %s").formatted(password, commandResult.getError()));
        }
        return commandResult.getOutput();
    }
}
