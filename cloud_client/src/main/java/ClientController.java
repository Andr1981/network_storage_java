import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class ClientController implements Initializable {
    private Network network;
    private String login;
    private boolean isAuth;
    private String currentFolder;
    private FileTransfer fileTransfer;

    @FXML
    TextField loginText;

    @FXML
    PasswordField passText;

    @FXML
    Button btnLogin;

    @FXML
    AnchorPane loginPane;

    @FXML
    ListView<String> listServerFiles;

    @FXML
    ListView<String> listClientFiles;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        currentFolder = "clientFolder";
        Path path =  Paths.get(currentFolder);

        try {
            List<String> filesList = Files.list(path).map((f)->f.getFileName().toString()).collect(Collectors.toList());
            ObservableList<String> files = FXCollections.observableArrayList(filesList);
            listClientFiles.setItems(files);
        }catch (Exception e){
            e.printStackTrace();
        }

        network = Network.getInstance();
        fileTransfer = network.getFileTransfer();

        network.setAuthCallBack(args -> {
            Platform.runLater(()->{
                showMessage(Alert.AlertType.INFORMATION, "Авторизация", "Клиент авторизован");
                loginPane.setManaged(false);
                loginPane.setVisible(false);
                isAuth = true;
                getFileListServer();
            });
        });

        fileTransfer.setErrorCallBack(args -> {
            Platform.runLater(()->{
                showMessage(Alert.AlertType.ERROR, "Ошибка", (String) args[0]);
            });
        });

        fileTransfer.setGetFileListCallBack(args -> {
            Platform.runLater(()->{
                List<String> filesList = (List<String>) args[0];
                ObservableList<String> files = FXCollections.observableArrayList(filesList);
                listServerFiles.setItems(files);
            });
        });

        fileTransfer.setDeleteFileCallBack(args -> {
            Platform.runLater(()->{
                showMessage(Alert.AlertType.INFORMATION, "Удаление", "Фйал " + args[0] + " удален");
                getFileListServer();
            });
        });

    }

    public void btnLoginOnAction(ActionEvent actionEvent) {

        login = loginText.getText().trim();
        String password = passText.getText().trim();
        if (login.isEmpty()){
            showMessage(Alert.AlertType.WARNING, "Внимание", "Не заполнен логин");
            return;
        }

        //команда
        ByteBuf buff = null;
        byte command = Command.AUTHORIZATION.getCommandCode();
        byte[] loginBytes = login.getBytes(StandardCharsets.UTF_8);
        byte[] passBytes = password.getBytes(StandardCharsets.UTF_8);

        buff = network.getCurrentChannel().alloc().directBuffer(1+4+loginBytes.length+4+passBytes.length);
        buff.writeByte(command);
        buff.writeInt(loginBytes.length);
        buff.writeBytes(loginBytes);
        buff.writeInt(passBytes.length);
        buff.writeBytes(passBytes);
        network.getCurrentChannel().writeAndFlush(buff);

    }

    public void btnSaveOnAction(ActionEvent actionEvent) {
        //сохранение настройки
    }

    public void btnDownloadOnAction(ActionEvent actionEvent) {

        if(!isAuth){
            showMessage(Alert.AlertType.WARNING,"Внимание", "Клиент не авторизован");
            return;
        }

        String downLoadFile = listServerFiles.getSelectionModel().getSelectedItem();
        network.getCurrentChannel().writeAndFlush(fileTransfer.sendSimpleMessage(Command.DOWNLOAD_FILE, downLoadFile));

    }

    public void btnUploadOnAction(ActionEvent actionEvent) {

        if(!isAuth){
            showMessage(Alert.AlertType.WARNING,"Внимание", "Клиент не авторизован");
            return;
        }

        Path uploadFile = Paths.get(currentFolder, listClientFiles.getSelectionModel().getSelectedItem());

        if(!Files.exists(uploadFile)){
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Внимание");
            alert.setHeaderText(null);
            alert.setContentText("Файл не найден");
            alert.showAndWait();
            return;
        }

        long currentFileLength = 0;

        try {
            currentFileLength = Files.size(uploadFile);
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Внимание");
            alert.setHeaderText(null);
            alert.setContentText("Ошибка определения размера файла");
            alert.showAndWait();
            return;
        }

        //команда
        ByteBuf buff = null;
        byte command = Command.UPLOAD_FILE_PROCESS.getCommandCode();
        byte[] fileNameBytes = uploadFile.getFileName().toString().getBytes(StandardCharsets.UTF_8);

        buff = network.getCurrentChannel().alloc().directBuffer(1+4+fileNameBytes.length+8);
        buff.writeByte(command);
        buff.writeInt(fileNameBytes.length);
        buff.writeBytes(fileNameBytes);
        buff.writeLong(currentFileLength);
        network.getCurrentChannel().writeAndFlush(buff);

        try {
            RandomAccessFile aFile = new RandomAccessFile(uploadFile.toFile(), "r");
            FileChannel inChannel = aFile.getChannel();
            long counter = 0;


            ByteBuf answer = null;
            ByteBuffer bufRead = ByteBuffer.allocate(1024);
            int bytesRead = inChannel.read(bufRead);
            counter = counter + bytesRead;
            while (bytesRead != -1 && counter <= currentFileLength) {

                answer = ByteBufAllocator.DEFAULT.directBuffer(1024, 5*1024);

                bufRead.flip();
                while(bufRead.hasRemaining()){
                    byte[] fileBytes = new byte[bytesRead];
                    bufRead.get(fileBytes);
                    answer.writeBytes(fileBytes);
                    network.getCurrentChannel().writeAndFlush(answer);
                }
                bufRead.clear();
                //answer.clear();
                bytesRead = inChannel.read(bufRead);
                counter = counter + bytesRead;
            }
            aFile.close();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Внимание");
            alert.setHeaderText(null);
            alert.setContentText("Файл отправлен");
            alert.showAndWait();
            return;
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void btnDeleteOnAction(ActionEvent actionEvent) {
        if(!isAuth){
            showMessage(Alert.AlertType.WARNING,"Внимание", "Клиент не авторизован");
            return;
        }
        String deleteFile = listServerFiles.getSelectionModel().getSelectedItem();
        network.getCurrentChannel().writeAndFlush(fileTransfer.requestDeleteFile(deleteFile));
    }

    public void btnRefreshOnAction(ActionEvent actionEvent) {
        if (isAuth){
            getFileListServer();
        }else{
            showMessage(Alert.AlertType.WARNING,"Внимание", "Клиент не авторизован");
        }
    }

    private void getFileListServer(){
        network.getCurrentChannel().writeAndFlush(fileTransfer.requestFileList(new String()));
    }

    private void showMessage(Alert.AlertType type, String title, String message){
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}
