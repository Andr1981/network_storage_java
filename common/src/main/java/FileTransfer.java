import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class FileTransfer {
    private long fileLength;
    private int byteLength = 1;
    private int intLength = 4;
    private int longLength = 8;

    private Callback getFileListCallBack;
    private Callback errorCallBack;
    private Callback deleteFileCallBack;

    private Command currentCommand = Command.NO_COMMAND;
    private int simpleLength;

    public void setGetFileListCallBack(Callback getFileListCallBack) {
        this.getFileListCallBack = getFileListCallBack;
    }

    public void setErrorCallBack(Callback errorCallBack) {
        this.errorCallBack = errorCallBack;
    }

    public void setDeleteFileCallBack(Callback deleteFileCallBack) {
        this.deleteFileCallBack = deleteFileCallBack;
    }

    public ByteBuf sendSimpleMessage(Command command, String message){
        byte[] srcDirNameBytes = message.getBytes(StandardCharsets.UTF_8);
        ByteBuf buff = ByteBufAllocator.DEFAULT.directBuffer(byteLength + intLength +  srcDirNameBytes.length);
        buff.writeByte(command.getCommandCode());
        buff.writeInt(srcDirNameBytes.length);
        buff.writeBytes(srcDirNameBytes);
        return buff;
    }

    public JobStage readErrorMessage(ByteBuf buf, JobStage currentStage){

        if(currentStage == JobStage.GET_ERROR_LENGTH){
            if (buf.readableBytes() >= intLength) {
                simpleLength = buf.readInt();
                currentStage = JobStage.GET_ERROR_MESSAGE;
            }
        }

        if(currentStage == JobStage.GET_ERROR_MESSAGE){
            if (buf.readableBytes() >= simpleLength) {
                byte[] arrayFilesByte = new byte[simpleLength];
                buf.readBytes(arrayFilesByte);
                String errorMessage = new String(arrayFilesByte, StandardCharsets.UTF_8);

                if(errorCallBack!=null)
                    errorCallBack.call(errorMessage);

                currentStage = JobStage.STANDBY;

            }
        }

        return currentStage;
    }

    public ByteBuf requestFileList(String srcDirName){
        return sendSimpleMessage(Command.GET_FILE_LIST, srcDirName);
    }

    public JobStage returnFileList(ByteBuf buf, JobStage currentStage){

        if(currentStage == JobStage.GET_FILE_LIST_LENGTH){
            if (buf.readableBytes() >= intLength) {
                simpleLength = buf.readInt();
                currentStage = JobStage.GET_FILE_LIST;
            }
        }

        if(currentStage == JobStage.GET_FILE_LIST){
            if (buf.readableBytes() >= simpleLength) {
                byte[] arrayFilesByte = new byte[simpleLength];
                buf.readBytes(arrayFilesByte);
                String[] arrayFiles = new String(arrayFilesByte, StandardCharsets.UTF_8).split("/");

                List<String> listFiles = Arrays.asList(arrayFiles);
                currentStage = JobStage.STANDBY;

                if(getFileListCallBack != null) {
                    getFileListCallBack.call(listFiles);
                }
            }
        }

        return currentStage;

    }

    public ByteBuf requestDeleteFile(String fileName){
        return  sendSimpleMessage(Command.DELETE_FILE, fileName);
    }

    public JobStage returnDeleteFile(ByteBuf buf, JobStage currentStage){

        if(currentStage==JobStage.GET_FILE_NAME_LENGTH){
            if (buf.readableBytes() >= intLength) {
                simpleLength = buf.readInt();
                currentStage = JobStage.GET_FILE_NAME;
            }
        }

        if(currentStage == JobStage.GET_FILE_NAME){

            if (buf.readableBytes() >= simpleLength) {
                byte[] fileNameByte = new byte[simpleLength];
                buf.readBytes(fileNameByte);
                String fileName = new String(fileNameByte, StandardCharsets.UTF_8);

                if(deleteFileCallBack != null){
                    deleteFileCallBack.call(fileName);
                }

                currentStage = JobStage.STANDBY;
            }

        }

        return currentStage;
    }

    public ByteBuf requestDownloadFile(String filename){
        return sendSimpleMessage(Command.DOWNLOAD_FILE, filename);
    }

}
