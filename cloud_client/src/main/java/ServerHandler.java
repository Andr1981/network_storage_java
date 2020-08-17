import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.nio.file.Path;


public class ServerHandler extends ChannelInboundHandlerAdapter {
    private FileTransfer fileTransfer;
    private Callback authCallBack, downloadFileCallBack, uploadFileCallBack, errorCallBack;
    private Command currentCommand = Command.NO_COMMAND;
    private JobStage currentStage = JobStage.STANDBY;
    private String currentFolder, currentFilename;
    private long currentFileLength;
    private Path downloadFile;

    public ServerHandler(FileTransfer fileTransfer, Callback authCallBack, Callback downloadFileCallBack, Callback uploadFileCallBack) {
        this.fileTransfer = fileTransfer;
        this.authCallBack = authCallBack;
        this.downloadFileCallBack = downloadFileCallBack;
        this.uploadFileCallBack = uploadFileCallBack;
        this.currentFolder = "clientFolder";
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = ((ByteBuf) msg);
        while (buf.readableBytes() > 0) {
            //определение входящей команды
            if(currentStage == JobStage.STANDBY) {
                currentCommand = CommandUtility.getCommand(buf.readByte());

                switch (currentCommand) {
                    case SUCCESS_AUTH:
                        if(authCallBack!=null)
                            authCallBack.call();
                        break;
                    case ERROR_SERVER:
                        currentStage =  JobStage.GET_ERROR_LENGTH;
                        break;
                    case RETURN_FILE_LIST:
                        currentStage = JobStage.GET_FILE_LIST_LENGTH;
                        break;
                    case DOWNLOAD_FILE_PROCESS:
                        currentStage = JobStage.GET_FILE_NAME_LENGTH;
                        break;
                    case DELETE_SUCCESS:
                        currentStage = JobStage.GET_FILE_NAME_LENGTH;
                        break;
                }
            }

            if(currentCommand == Command.ERROR_SERVER){
                currentStage = fileTransfer.readErrorMessage(buf, currentStage);
            }

            if(currentCommand == Command.RETURN_FILE_LIST){
                currentStage = fileTransfer.returnFileList(buf, currentStage);
            }

            if(currentCommand == Command.DOWNLOAD_FILE_PROCESS){

                currentStage = fileTransfer.readFileParameters(buf, currentStage);

                try {
                    currentStage = fileTransfer.readFile(buf, currentStage);
                } catch (IOException e) {
                    e.printStackTrace();
                    fileTransfer.callErrorCallBack("Ошибка загрузки файла");
                    fileTransfer.closeFile();
                    currentStage = JobStage.STANDBY;
                }

            }

            if(currentCommand == Command.DELETE_SUCCESS){
                currentStage = fileTransfer.returnDeleteFile(buf, currentStage);
            }

            if (buf.readableBytes() == 0) {
                buf.release();
            }

        }
    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
