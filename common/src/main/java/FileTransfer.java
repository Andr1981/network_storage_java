import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class FileTransfer {
    private long fileLength;
    private int byteLength = 1;
    private int intLength = 4;
    private int longLength = 8;

    private String currentFolder;
    private String currentFilename;
    private Path downloadUploadFile;
    private RandomAccessFile aFile;
    private FileChannel inChannel;
    private long counter;
    private int tempCount;

    private Callback getFileListCallBack;
    private Callback errorCallBack;
    private Callback deleteFileCallBack;
    private Callback uploadFileCallBack;
    private Callback informationCallBack;
    private Callback progressBarCallBack;

    private Command currentCommand = Command.NO_COMMAND;
    private int simpleLength;

    public void closeFile(){
        if(aFile != null){
            try {
                aFile.close();
            } catch (IOException e) {
                //e.printStackTrace();
            }
        }
    }

    public void setCurrentFolder(String currentFolder) {
        this.currentFolder = currentFolder;
    }

    public void setGetFileListCallBack(Callback getFileListCallBack) {
        this.getFileListCallBack = getFileListCallBack;
    }

    public void setErrorCallBack(Callback errorCallBack) {
        this.errorCallBack = errorCallBack;
    }

    public void setInformationCallBack(Callback informationCallBack) {
        this.informationCallBack = informationCallBack;
    }

    public void setProgressBarCallBack(Callback progressBarCallBack) {
        this.progressBarCallBack = progressBarCallBack;
    }

    public void setUploadFileCallBack(Callback uploadFileCallBack) {
        this.uploadFileCallBack = uploadFileCallBack;
    }

    public void callInformationCallBack(String message){
        if(informationCallBack !=null){
            informationCallBack.call(message);
        }
    }

    public void callErrorCallBack(String message){
        if(errorCallBack !=null){
            errorCallBack.call(message);
        }
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

    public JobStage readFileParameters(ByteBuf buf, JobStage currentStage){

        if(currentStage==JobStage.GET_FILE_NAME_LENGTH){

            downloadUploadFile = null;
            aFile = null;
            inChannel = null;

            if (buf.readableBytes() >= intLength) {
                simpleLength = buf.readInt();
                currentStage = JobStage.GET_FILE_NAME;
            }
        }

        if(currentStage == JobStage.GET_FILE_NAME){
            if (buf.readableBytes() >= simpleLength) {
                byte[] fileNameByte = new byte[simpleLength];
                buf.readBytes(fileNameByte);
                currentFilename = new String(fileNameByte, StandardCharsets.UTF_8);
                currentStage = JobStage.GET_FILE_LENGTH;
            }
        }

        if(currentStage == JobStage.GET_FILE_LENGTH){
            if (buf.readableBytes() >= longLength) {
                fileLength = buf.readLong();
                currentStage = JobStage.START_FILE_OPERATION;
            }
        }

        return currentStage;

    }

    public JobStage readFile(ByteBuf buf,  JobStage currentStage) throws IOException {

        if(currentStage == JobStage.START_FILE_OPERATION){
            counter         = 0l;
            tempCount       = 0;
            downloadUploadFile    = Paths.get(currentFolder, currentFilename);
            aFile           = new RandomAccessFile(downloadUploadFile.toFile(), "rw");
            inChannel       = aFile.getChannel();
            currentStage = JobStage.GET_FILE;
        }

        if(currentStage == JobStage.GET_FILE){


            while (buf.readableBytes()>0 && counter < fileLength){
                tempCount = inChannel.write(buf.nioBuffer());
                counter = counter + tempCount;
                buf.readerIndex(buf.readerIndex() + tempCount);//-> buf.readableBytes()=0
            }

            if(counter == fileLength) {
                aFile.close();
                currentStage = JobStage.STANDBY;
            }
        }

        return currentStage;
    }

    public ByteBuf requestUploadFile(String filename){
        currentFilename = filename;
        downloadUploadFile = Paths.get(currentFolder, currentFilename);

        if(!Files.exists(downloadUploadFile)){
            if(errorCallBack != null){
                errorCallBack.call("Файл не найден");
            }
            return null;
        }

        try {
            fileLength = Files.size(downloadUploadFile);
        } catch (IOException e) {
            if(errorCallBack != null){
                errorCallBack.call("Ошибка определения размера файла");
            }
            return null;
        }

        byte[] fileNameBytes = downloadUploadFile.getFileName().toString().getBytes(StandardCharsets.UTF_8);
        ByteBuf buff = ByteBufAllocator.DEFAULT.directBuffer(byteLength + intLength + fileNameBytes.length + longLength);
        buff.writeByte(Command.UPLOAD_FILE_PROCESS.getCommandCode());
        buff.writeInt(fileNameBytes.length);
        buff.writeBytes(fileNameBytes);
        buff.writeLong(fileLength);

        try {
            aFile = new RandomAccessFile(downloadUploadFile.toFile(), "r");
            inChannel = aFile.getChannel();
            counter = 0l;
        } catch (FileNotFoundException e) {
            return null;
        }

        return buff;

    }

    public void sendingUploadFile(Channel channel){

        ByteBuf answer = null;

        try {
            ByteBuffer bufRead = ByteBuffer.allocate(1024);
            int bytesRead = inChannel.read(bufRead);
            counter = counter + bytesRead;

            while (bytesRead != -1 && counter <= fileLength) {

                answer = ByteBufAllocator.DEFAULT.directBuffer(1024, 5*1024);

                bufRead.flip();
                while(bufRead.hasRemaining()){
                    byte[] fileBytes = new byte[bytesRead];
                    bufRead.get(fileBytes);
                    answer.writeBytes(fileBytes);
                    channel.writeAndFlush(answer);
                }
                bufRead.clear();
                bytesRead = inChannel.read(bufRead);
                counter = counter + bytesRead;
            }

            if(uploadFileCallBack != null){
                uploadFileCallBack.call(currentFilename);
            }

        } catch (IOException e) {
            callErrorCallBack("Ошибка при выгрузке файла");
            e.printStackTrace();
        } finally {
            try {
                aFile.close();
            } catch (IOException e) {

            }
        }





    }

}
