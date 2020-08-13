import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;


public class ServerHandler extends ChannelInboundHandlerAdapter {
    private FileTransfer fileTransfer;
    private Callback authCallBack, downloadFileCallBack, uploadFileCallBack, errorCallBack;
    private Command currentCommand = Command.NO_COMMAND;
    private JobStage currentStage = JobStage.STANDBY;
    private int lengthInt;
    private String currentFolder, currentFilename;
    private long currentFileLength;
    private Path downloadFile;
    private RandomAccessFile aFile;
    private FileChannel inChannel;
    private long counter = 0;
    private int tempCount = 0;

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

                if(currentStage==JobStage.GET_FILE_NAME_LENGTH){
                    if (buf.readableBytes() >= 4) {
                        lengthInt = buf.readInt();
                        currentStage = JobStage.GET_FILE_NAME;
                    }
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

                        downloadFile = Paths.get(currentFolder, currentFilename);
                        aFile = new RandomAccessFile(downloadFile.toFile(), "rw");
                        inChannel = aFile.getChannel();
                        counter = 0;
                        tempCount = 0;
                    }
                }

                if(currentStage == JobStage.GET_FILE){

                    while (buf.readableBytes()>0 && counter < currentFileLength){
                        tempCount = inChannel.write(buf.nioBuffer());
                        counter = counter + tempCount;
                        buf.readerIndex(buf.readerIndex() + tempCount);//-> buf.readableBytes()=0
                    }

                    if(counter == currentFileLength) {
                        aFile.close();
                        currentStage = JobStage.STANDBY;
                        currentCommand = Command.NO_COMMAND;
                    }

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
