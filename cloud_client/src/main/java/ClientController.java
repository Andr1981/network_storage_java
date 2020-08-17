import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.net.URL;
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
    HBox loginPane;

    @FXML
    TableView<FileInfo> listServerFiles;

    @FXML
    TableView<FileInfo> listClientFiles;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        TableColumn<FileInfo, String> fileTypeColumn = new TableColumn<>();
        fileTypeColumn.setCellValueFactory(param->new SimpleStringProperty(param.getValue().getType().getName()));
        fileTypeColumn.setPrefWidth(24);

        TableColumn<FileInfo, String> fileNameColumn = new TableColumn<>("Имя файла");
        fileNameColumn.setCellValueFactory(param->new SimpleStringProperty(param.getValue().getFilename()));
        fileNameColumn.setPrefWidth(240);

        listClientFiles.getColumns().add(fileTypeColumn);
        listClientFiles.getColumns().add(fileNameColumn);

        listServerFiles.getColumns().add(fileTypeColumn);
        listServerFiles.getColumns().add(fileNameColumn);

        currentFolder = "clientFolder";
        Path path =  Paths.get(currentFolder);
        updateList(path);

        network = Network.getInstance();
        fileTransfer = network.getFileTransfer();
        fileTransfer.setCurrentFolder(currentFolder);

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

        fileTransfer.setInformationCallBack(args -> {
            Platform.runLater(()->{
                showMessage(Alert.AlertType.INFORMATION, "Информация", (String) args[0]);
            });
        });

        fileTransfer.setUploadFileCallBack(args -> {
            Platform.runLater(()->{
                showMessage(Alert.AlertType.INFORMATION, "Выгрузка", "Файл " + args[0] + " выгружен");
                getFileListServer();
            });
        });

        fileTransfer.setGetFileListCallBack(args -> {
            Platform.runLater(()->{
                List<String> filesList = (List<String>) args[0];
                ObservableList<String> files = FXCollections.observableArrayList(filesList);

            });
        });

        fileTransfer.setDeleteFileCallBack(args -> {
            Platform.runLater(()->{
                showMessage(Alert.AlertType.INFORMATION, "Удаление", "Файл " + args[0] + " удален");
                getFileListServer();
            });
        });

    }

    public void updateList(Path path){

        try {
            listClientFiles.getItems().clear();
            listClientFiles.getItems().addAll(Files.list(path).map(FileInfo::new).collect(Collectors.toList()));
            listClientFiles.sort();
        } catch (IOException e) {
            showMessage(Alert.AlertType.ERROR, "Ошибка", "Ошибка чтения файлов");
        }
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

        //String downLoadFile = listServerFiles.getSelectionModel().getSelectedItem();
        String downLoadFile = new String();
        network.getCurrentChannel().writeAndFlush(fileTransfer.requestDownloadFile(downLoadFile));

    }

    public void btnUploadOnAction(ActionEvent actionEvent) {

        if(!isAuth){
            showMessage(Alert.AlertType.WARNING,"Внимание", "Клиент не авторизован");
            return;
        }


        ByteBuf buff = fileTransfer.requestUploadFile(new String());

        if(buff == null){
            return;
        }

        network.getCurrentChannel().writeAndFlush(buff);

        fileTransfer.sendingUploadFile(network.getCurrentChannel());

    }

    public void btnDeleteOnAction(ActionEvent actionEvent) {
        if(!isAuth){
            showMessage(Alert.AlertType.WARNING,"Внимание", "Клиент не авторизован");
            return;
        }

        String deleteFile = new String();
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
