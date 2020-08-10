import callback.Callback;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class ServerHandler extends ChannelInboundHandlerAdapter {
    private Callback authCallBack, getFileListCallBack, downloadFileCallBack, uploadFileCallBack, errorCallBack;
    private Command currentCommand = Command.NO_COMMAND;
    private JobStage currentStage = JobStage.STANDBY;
    private int lengthInt;
    private String currentFolder, currentFilename;
    private long currentFileLength;

    public ServerHandler(Callback authCallBack,Callback getFileListCallBack,Callback downloadFileCallBack, Callback uploadFileCallBack, Callback errorCallBack) {
        this.authCallBack = authCallBack;
        this.getFileListCallBack = getFileListCallBack;
        this.downloadFileCallBack = downloadFileCallBack;
        this.uploadFileCallBack = uploadFileCallBack;
        this.errorCallBack = errorCallBack;
        this.currentFolder = "clientFolder";
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = ((ByteBuf) msg);
        while (buf.readableBytes() > 0) {
            //определение входящей команды
            if (currentStage == JobStage.STANDBY) {
                currentCommand = CommandUtility.getCommand(buf.readByte());

                switch (currentCommand) {
                    case SUCCESS_AUTH:
                        if(authCallBack!=null)
                            authCallBack.call();
                        break;
                    case ERROR_SERVER:
                        if(errorCallBack!=null)
                            errorCallBack.call();
                        break;
                    case RETURN_FILE_LIST:
                        currentStage = JobStage.GET_FILE_LIST_LENGTH;
                        break;
                    case DOWNLOAD_FILE_PROCESS:
                        currentStage = JobStage.GET_FILE_NAME_LENGTH;
                        break;
                }
            }

            if (currentCommand == Command.RETURN_FILE_LIST){

                if(currentStage == JobStage.GET_FILE_LIST_LENGTH){
                    if (buf.readableBytes() >= 4) {
                        lengthInt = buf.readInt();
                        currentStage = JobStage.GET_FILE_LIST;
                    }
                }

                if(currentStage == JobStage.GET_FILE_LIST){
                    if (buf.readableBytes() >= lengthInt) {
                        byte[] arrayFilesByte = new byte[lengthInt];
                        buf.readBytes(arrayFilesByte);
                        String[] arrayFiles = new String(arrayFilesByte, StandardCharsets.UTF_8).split("/");

                        List<String> listFiles = Arrays.asList(arrayFiles);
                        getFileListCallBack.call(listFiles);
                        currentStage = JobStage.STANDBY;
                        currentCommand = Command.NO_COMMAND;
                    }
                }
            }

            if(currentCommand == Command.DOWNLOAD_FILE_PROCESS){

                if (buf.readableBytes() >= 4) {
                    lengthInt = buf.readInt();
                    currentStage = JobStage.GET_FILE_NAME;
                }

                if(currentStage == JobStage.GET_FILE_NAME){
                    if (buf.readableBytes() >= lengthInt) {
                        byte[] fileNameByte = new byte[lengthInt];
                        buf.readBytes(fileNameByte);
                        currentFilename = new String(fileNameByte, "UTF-8");
                        currentStage = JobStage.GET_FILE_LENGTH;
                    }
                }

                if(currentStage == JobStage.GET_FILE_LENGTH){
                    if (buf.readableBytes() >= 8) {
                        currentFileLength = buf.readLong();
                        currentStage = JobStage.GET_FILE;
                    }
                }

                if(currentStage == JobStage.GET_FILE){
                    Path downloadFile = Paths.get(currentFolder, currentFilename);
                    RandomAccessFile aFile = new RandomAccessFile(downloadFile.toFile(), "w");
                    long counter = 0;
                    while (buf.readableBytes()>0 && counter <= currentFileLength){

                    }

                }
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
