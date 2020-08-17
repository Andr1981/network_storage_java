import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class ClientHandlerNetty extends ChannelInboundHandlerAdapter {
    private String rootFolder;
    private AuthServer authServer;
    private FileTransfer fileTransfer;
    private String login;
    private boolean isAuth;

    private int lengthInt, lengthPass;


    private String loginFolder;
    private String message;
    private Command currentCommand = Command.NO_COMMAND;
    private JobStage currentStage = JobStage.STANDBY;

    private String currentFilename;
    private long currentFileLength;
    private Path downloadFile;
    private RandomAccessFile aFile;
    private FileChannel inChannel;
    private long counter = 0;
    private int tempCount = 0;

    public ClientHandlerNetty(String rootFolder, AuthServer authServer) {
        this.rootFolder = rootFolder;
        this.authServer = authServer;
        this.fileTransfer = new FileTransfer();
        this.isAuth = false;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Клиент подключился");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = ((ByteBuf) msg);

        while (buf.readableBytes() > 0) {
            //определение входящей команды
            if (currentStage == JobStage.STANDBY){
                currentCommand = Command.NO_COMMAND;
                currentCommand = CommandUtility.getCommand(buf.readByte());
                switch (currentCommand){
                    case AUTHORIZATION:
                        currentStage = JobStage.GET_LENGTH_LOGIN;
                        break;
                    case GET_FILE_LIST:
                        currentStage = JobStage.GET_FILE_LIST;
                        break;
                    case DOWNLOAD_FILE:
                        currentStage = JobStage.GET_FILE_NAME_LENGTH;
                        break;
                    case UPLOAD_FILE_PROCESS:
                        currentStage = JobStage.GET_FILE_NAME_LENGTH;
                        break;
                    case DELETE_FILE:
                        currentStage = JobStage.GET_FILE_NAME_LENGTH;
                }

            }

            //последовательность действий
            if(currentCommand == Command.AUTHORIZATION){

                if(currentStage == JobStage.GET_LENGTH_LOGIN){
                    if (buf.readableBytes() >= 4) {
                        lengthInt = buf.readInt();
                        currentStage = JobStage.GET_LOGIN;
                    }
                }

                if(currentStage == JobStage.GET_LOGIN){
                    if(buf.readableBytes()>=lengthInt){
                        byte[] loginByte = new byte[lengthInt];
                        buf.readBytes(loginByte);
                        login = new String(loginByte, "UTF-8");
                        currentStage = JobStage.GET_LENGTH_PASS;
                    }
                }

                if(currentStage == JobStage.GET_LENGTH_PASS){
                    if(buf.readableBytes()>=4){
                        lengthPass = buf.readInt();
                        currentStage = JobStage.GET_PASS;
                    }
                }

                if(currentStage == JobStage.GET_PASS){
                    if(buf.readableBytes()>=lengthPass) {
                        byte[] passByte = new byte[lengthPass];
                        buf.readBytes(passByte);
                        String pass = new String(passByte, "UTF-8");

                        ByteBuf answer = null;
                        if (authServer.authSuccess(login, pass)) {
                            answer = ByteBufAllocator.DEFAULT.directBuffer(1);
                            answer.writeByte(Command.SUCCESS_AUTH.getCommandCode());
                            ctx.writeAndFlush(answer);
                            System.out.println("Auth success");

                            loginFolder = loginFolder + "/" + login;
                            Path path = Paths.get(rootFolder, login);
                            if (!Files.exists(path))
                                Files.createDirectory(path);

                            isAuth = true;

                        } else {
                            message = "Ошибка авторизации";

                            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

                            answer = ByteBufAllocator.DEFAULT.directBuffer(1 + 4 + messageBytes.length);
                            answer.writeByte(Command.ERROR_SERVER.getCommandCode());
                            answer.writeInt(messageBytes.length);
                            answer.writeBytes(messageBytes);
                            ctx.writeAndFlush(answer);

                            System.out.println("Auth failure");
                        }
                        currentStage = JobStage.STANDBY;
                        currentCommand = Command.NO_COMMAND;
                    }
                }

            }

            if(currentStage != JobStage.STANDBY) {
                if (isAuth) {

                    if (currentCommand == Command.GET_FILE_LIST) {
                        if (currentStage == JobStage.GET_FILE_LIST) {

                            Path path = Paths.get(rootFolder, login);
                            String filesList = Files.list(path).map((f) -> f.getFileName().toString()).collect(Collectors.joining("/", "", ""));
                            ctx.writeAndFlush(fileTransfer.sendSimpleMessage(Command.RETURN_FILE_LIST, filesList));
                            currentStage = JobStage.STANDBY;

                            System.out.println("Послали список файлов");

                        }
                    }

                    if (currentCommand == Command.DOWNLOAD_FILE) {

                        if (currentStage == JobStage.GET_FILE_NAME_LENGTH) {
                            if (buf.readableBytes() >= 4) {
                                lengthInt = buf.readInt();
                                currentStage = JobStage.GET_FILE_NAME;
                            }
                        }

                        if (currentStage == JobStage.GET_FILE_NAME) {
                            if (buf.readableBytes() >= lengthInt) {
                                byte[] fileNameByte = new byte[lengthInt];
                                buf.readBytes(fileNameByte);
                                currentFilename = new String(fileNameByte, "UTF-8");
                                currentStage = JobStage.CHECK_FILE;
                            }
                        }

                        if (currentStage == JobStage.CHECK_FILE) {
                            Path downLoadFile = Paths.get(rootFolder, login, currentFilename);
                            if (Files.exists(downLoadFile)) {

                                ByteBuf answer1 = null;

                                currentFileLength = Files.size(downLoadFile);
                                byte[] fileNameBytes = downLoadFile.getFileName().toString().getBytes(StandardCharsets.UTF_8);
                                answer1 = ByteBufAllocator.DEFAULT.directBuffer(1 + 4 + fileNameBytes.length + 8);
                                answer1.writeByte(Command.DOWNLOAD_FILE_PROCESS.getCommandCode());
                                answer1.writeInt(fileNameBytes.length);
                                answer1.writeBytes(fileNameBytes);
                                answer1.writeLong(currentFileLength);
                                ctx.writeAndFlush(answer1);

                                RandomAccessFile aFile = new RandomAccessFile(downLoadFile.toFile(), "r");
                                FileChannel inChannel = aFile.getChannel();
                                long counter = 0;


                                ByteBuf answer = null;
                                ByteBuffer bufRead = ByteBuffer.allocate(1024);
                                int bytesRead = inChannel.read(bufRead);
                                counter = counter + bytesRead;
                                while (bytesRead != -1 && counter <= currentFileLength) {

                                    answer = ByteBufAllocator.DEFAULT.directBuffer(1024, 5 * 1024);

                                    bufRead.flip();
                                    while (bufRead.hasRemaining()) {
                                        byte[] fileBytes = new byte[bytesRead];
                                        bufRead.get(fileBytes);
                                        answer.writeBytes(fileBytes);
                                        ctx.writeAndFlush(answer);
                                    }
                                    bufRead.clear();
                                    //answer.clear();
                                    bytesRead = inChannel.read(bufRead);
                                    counter = counter + bytesRead;
                                }
                                aFile.close();
                                System.out.println("Файл ушел");
                                currentStage = JobStage.STANDBY;
                                currentCommand = Command.NO_COMMAND;

                            } else {
                                ByteBuf answer = null;
                                message = "Файл не найден";
                                byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
                                answer = ByteBufAllocator.DEFAULT.directBuffer(1 + 4 + messageBytes.length);
                                answer.writeByte(Command.ERROR_SERVER.getCommandCode());
                                answer.writeInt(messageBytes.length);
                                answer.writeBytes(messageBytes);
                                ctx.writeAndFlush(answer);

                                System.out.println("Файл не найден");

                                currentStage = JobStage.STANDBY;
                                currentCommand = Command.NO_COMMAND;
                            }
                        }

                    }

                    if (currentCommand == Command.UPLOAD_FILE_PROCESS) {

                        if (currentStage == JobStage.GET_FILE_NAME_LENGTH) {
                            if (buf.readableBytes() >= 4) {
                                lengthInt = buf.readInt();
                                currentStage = JobStage.GET_FILE_NAME;
                            }
                        }

                        if (currentStage == JobStage.GET_FILE_NAME) {
                            if (buf.readableBytes() >= lengthInt) {
                                byte[] fileNameByte = new byte[lengthInt];
                                buf.readBytes(fileNameByte);
                                currentFilename = new String(fileNameByte, "UTF-8");
                                currentStage = JobStage.GET_FILE_LENGTH;
                            }
                        }

                        if (currentStage == JobStage.GET_FILE_LENGTH) {
                            if (buf.readableBytes() >= 8) {
                                currentFileLength = buf.readLong();
                                currentStage = JobStage.GET_FILE;

                                downloadFile = Paths.get(rootFolder, login, currentFilename);
                                aFile = new RandomAccessFile(downloadFile.toFile(), "rw");
                                inChannel = aFile.getChannel();
                                counter = 0;
                                tempCount = 0;
                            }
                        }

                        if (currentStage == JobStage.GET_FILE) {

                            while (buf.readableBytes() > 0 && counter < currentFileLength) {
                                tempCount = inChannel.write(buf.nioBuffer());
                                counter = counter + tempCount;
                                buf.readerIndex(buf.readerIndex() + tempCount);//-> buf.readableBytes()=0
                            }

                            if (counter == currentFileLength) {
                                aFile.close();
                                currentStage = JobStage.STANDBY;
                                currentCommand = Command.NO_COMMAND;
                            }

                        }
                    }

                    if (currentCommand == Command.DELETE_FILE) {
                        if (currentStage == JobStage.GET_FILE_NAME_LENGTH) {
                            if (buf.readableBytes() >= 4) {
                                lengthInt = buf.readInt();
                                currentStage = JobStage.GET_FILE_NAME;
                            }
                        }

                        if (currentStage == JobStage.GET_FILE_NAME) {
                            if (buf.readableBytes() >= lengthInt) {
                                byte[] fileNameByte = new byte[lengthInt];
                                buf.readBytes(fileNameByte);
                                currentFilename = new String(fileNameByte, "UTF-8");
                                currentStage = JobStage.CHECK_FILE;
                            }
                        }

                        if (currentStage == JobStage.CHECK_FILE) {
                            Path deleteFile = Paths.get(rootFolder, login, currentFilename);
                            if (Files.exists(deleteFile)) {

                                currentStage = JobStage.STANDBY;

                                try {
                                    Files.delete(deleteFile);
                                    ctx.writeAndFlush(fileTransfer.sendSimpleMessage(Command.DELETE_SUCCESS, deleteFile.getFileName().toString()));
                                    System.out.println("Файл удален");
                                } catch (IOException e) {
                                    ctx.writeAndFlush(fileTransfer.sendSimpleMessage(Command.ERROR_SERVER, "Ошибка удаления файла"));
                                    System.out.println("Ошибка удаления" + e.getMessage());
                                }

                            } else {

                                ctx.writeAndFlush(fileTransfer.sendSimpleMessage(Command.ERROR_SERVER, "Файл не найден"));
                                currentStage = JobStage.STANDBY;
                                System.out.println("Файл не найден");

                            }
                        }

                    }

                } else {

                    ctx.writeAndFlush(fileTransfer.sendSimpleMessage(Command.ERROR_SERVER, "Авторизуйтесь"));
                    currentStage = JobStage.STANDBY;

                    System.out.println("Неразрешенные действия");

                }
            }
        }

        if (buf.readableBytes() == 0) {
            buf.release();
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

        System.out.println("Клиент отключился");

        ctx.close();
    }
}
