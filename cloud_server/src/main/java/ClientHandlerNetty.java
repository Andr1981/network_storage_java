import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class ClientHandlerNetty extends ChannelInboundHandlerAdapter {
    private String rootFolder;
    private AuthServer authServer;
    private FileTransfer fileTransfer;
    private AuthTransfer authTransfer;

    private boolean isAuth;

    private Command currentCommand = Command.NO_COMMAND;
    private JobStage currentStage = JobStage.STANDBY;


    public ClientHandlerNetty(String rootFolder, AuthServer authServer) {
        this.rootFolder = rootFolder;
        this.authServer = authServer;
        this.fileTransfer = new FileTransfer();
        this.authTransfer = new AuthTransfer();
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
                currentStage = authTransfer.readLoginPassword(buf, currentStage);

                if(currentStage == JobStage.LOG_IN){
                    if (authServer.authSuccess(authTransfer.getLogin(), authTransfer.getPassword())) {
                        isAuth = true;
                        Path path = Paths.get(rootFolder, authTransfer.getLogin());
                        if (!Files.exists(path)){
                            try {
                                Files.createDirectory(path);
                            } catch (IOException e) {
                                isAuth = false;
                            }
                        }

                        if(isAuth) {
                            fileTransfer.setCurrentFolder(path.toString());
                            ctx.writeAndFlush(authTransfer.sendAuthSuccess());
                            System.out.println("Клиент авторизовался");
                        }

                    } else {
                        fileTransfer.sendSimpleMessage(Command.ERROR_SERVER, "Ошибка авторизации");
                        System.out.println("Ошибка авторизации");
                    }
                    currentStage = JobStage.STANDBY;
                }

            }

            if(currentStage != JobStage.STANDBY) {
                if (isAuth) {

                    if (currentCommand == Command.GET_FILE_LIST) {
                        if (currentStage == JobStage.GET_FILE_LIST) {

                            Path path = Paths.get(rootFolder, authTransfer.getLogin());
                            String filesList = Files.list(path).map((f) -> f.getFileName().toString()).collect(Collectors.joining("/", "", ""));
                            ctx.writeAndFlush(fileTransfer.sendSimpleMessage(Command.RETURN_FILE_LIST, filesList));
                            currentStage = JobStage.STANDBY;

                            System.out.println("Послали список файлов");

                        }
                    }

                    if (currentCommand == Command.DOWNLOAD_FILE) {

                        currentStage = fileTransfer.readFileName(buf, currentStage);
                        currentStage = fileTransfer.checkFile(currentStage);

                        if (currentStage == JobStage.START_FILE_OPERATION) {
                            ctx.writeAndFlush(fileTransfer.sendDownloadFile());
                            fileTransfer.sendingUploadFile(ctx.channel());
                            System.out.println("Файл ушел");
                            currentStage = JobStage.STANDBY;
                        } else {
                            ctx.writeAndFlush(fileTransfer.sendSimpleMessage(Command.ERROR_SERVER, "Файл не найден"));
                            System.out.println("Файл не найден");
                            currentStage = JobStage.STANDBY;
                        }

                    }

                    if (currentCommand == Command.UPLOAD_FILE_PROCESS) {
                        currentStage = fileTransfer.readFileParameters(buf, currentStage);
                        currentStage = fileTransfer.readFile(buf, currentStage);
                    }

                    if (currentCommand == Command.DELETE_FILE) {

                        currentStage = fileTransfer.readFileName(buf, currentStage);
                        currentStage = fileTransfer.checkFile(currentStage);

                        if (currentStage == JobStage.START_FILE_OPERATION) {
                            currentStage = JobStage.STANDBY;
                            Path deleteFile = Paths.get(rootFolder, authTransfer.getLogin(), fileTransfer.getCurrentFilename());
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
