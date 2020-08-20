import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
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
    private AuthTransfer authTransfer;

    @FXML
    TextField loginText;

    @FXML
    TextField pathField;

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

    @FXML
    ProgressBar progress;

    @FXML
    ComboBox<String> diskBox;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        initializeTableView(listClientFiles);
        initializeTableView(listServerFiles);

        listClientFiles.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if(event.getClickCount() == 2){
                    Path newPath = Paths.get(pathField.getText()).resolve(listClientFiles.getSelectionModel().getSelectedItem().getFilename());
                    if(Files.isDirectory(newPath)){
                        updateList(newPath);
                        currentFolder = newPath.getFileName().toString();
                    }
                }
            }
        });

        currentFolder = "clientFolder";
        Path path =  Paths.get(currentFolder);
        if(!Files.exists(path)){
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        diskBox.getItems().clear();
        for(Path p : FileSystems.getDefault().getRootDirectories()){
            diskBox.getItems().add(p.toString());
        }
        diskBox.getSelectionModel().select(0);

        updateList(path);

        network = Network.getInstance();
        fileTransfer = network.getFileTransfer();
        fileTransfer.setCurrentFolder(currentFolder);

        authTransfer = network.getAuthTransfer();

        authTransfer.setLogInCallback(args -> {
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

        fileTransfer.setDownloadFileCallBack(args -> {
            Platform.runLater(()->{
                showMessage(Alert.AlertType.INFORMATION, "Загрузка", "Файл " + args[0] + " загружен");
                updateList(Paths.get(currentFolder));
            });
        });

        fileTransfer.setGetFileListCallBack(args -> {
            Platform.runLater(()->{
                List<String> filesList = (List<String>) args[0];
                listServerFiles.getItems().clear();
                listServerFiles.getItems().addAll(filesList.stream().map(FileInfo::new).collect(Collectors.toList()));
                listServerFiles.sort();
            });
        });

        fileTransfer.setDeleteFileCallBack(args -> {
            Platform.runLater(()->{
                showMessage(Alert.AlertType.INFORMATION, "Удаление", "Файл " + args[0] + " удален");
                getFileListServer();
            });
        });

        fileTransfer.setProgressBarCallBack(args -> {
            Platform.runLater(()->{
                progress.setProgress(args[0]);
            });
        });

    }

    public void updateList(Path path){

        try {
            pathField.setText(path.normalize().toAbsolutePath().toString());
            listClientFiles.getItems().clear();
            listClientFiles.getItems().addAll(Files.list(path).map(FileInfo::new).collect(Collectors.toList()));
            listClientFiles.sort();
        } catch (IOException e) {
            showMessage(Alert.AlertType.ERROR, "Ошибка", "Ошибка чтения файлов");
        }
    }

    private void initializeTableView(TableView<FileInfo> tableView){
        TableColumn<FileInfo, String> fileTypeColumn = new TableColumn<>();
        fileTypeColumn.setCellValueFactory(param->new SimpleStringProperty(param.getValue().getType().getName()));
        fileTypeColumn.setPrefWidth(24);

        TableColumn<FileInfo, String> fileNameColumn = new TableColumn<>("Имя файла");
        fileNameColumn.setCellValueFactory(param->new SimpleStringProperty(param.getValue().getFilename()));
        fileNameColumn.setPrefWidth(240);

        TableColumn<FileInfo, Long> fileSizeColumn = new TableColumn<>("Размер");
        fileSizeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getSize()));
        fileSizeColumn.setPrefWidth(120);

        fileSizeColumn.setCellFactory(column->{
            return  new TableCell<FileInfo,Long>(){
                @Override
                protected void updateItem(Long item, boolean empty) {
                    if(item == null || empty){
                        setText(null);
                        setStyle("");
                    }else{
                        String text = String.format("%,d bytes", item);
                        if(item == -1L){
                            text = "[DIR]";
                        }
                        setText(text);
                    }
                }
            };
        });

        tableView.getColumns().addAll(fileTypeColumn, fileNameColumn, fileSizeColumn);
        tableView.getSortOrder().add(fileTypeColumn);




    }

    public void btnLoginOnAction(ActionEvent actionEvent) {

        login = loginText.getText().trim();
        String password = passText.getText().trim();
        if (login.isEmpty()){
            showMessage(Alert.AlertType.WARNING, "Внимание", "Не заполнен логин");
            return;
        }

        network.getCurrentChannel().writeAndFlush(authTransfer.requestSingIn(login, password));

    }

    public void mnUnLogin(ActionEvent actionEvent) {

    }

    public void btnSaveOnAction(ActionEvent actionEvent) {
        //сохранение настройки
    }

    public void btnDownloadOnAction(ActionEvent actionEvent) {

        if(!isAuth){
            showMessage(Alert.AlertType.WARNING,"Внимание", "Клиент не авторизован");
            return;
        }

        progress.progressProperty().unbind();
        progress.setProgress(0);
        fileTransfer.setCurrentFolder(currentFolder);
        String downLoadFile = listServerFiles.getFocusModel().getFocusedItem().getFilename();
        network.getCurrentChannel().writeAndFlush(fileTransfer.requestDownloadFile(downLoadFile));

    }

    public void btnUploadOnAction(ActionEvent actionEvent) {

        if(!isAuth){
            showMessage(Alert.AlertType.WARNING,"Внимание", "Клиент не авторизован");
            return;
        }

        progress.progressProperty().unbind();
        progress.setProgress(0);
        fileTransfer.setCurrentFolder(currentFolder);
        ByteBuf buff = fileTransfer.requestUploadFile(listClientFiles.getFocusModel().getFocusedItem().getFilename());

        if(buff == null){
            return;
        }

        network.getCurrentChannel().writeAndFlush(buff);

        new Thread(()->{fileTransfer.sendingUploadFile(network.getCurrentChannel());}).start();

    }

    public void btnDeleteOnAction(ActionEvent actionEvent) {
        if(!isAuth){
            showMessage(Alert.AlertType.WARNING,"Внимание", "Клиент не авторизован");
            return;
        }
        String deleteFile = listServerFiles.getFocusModel().getFocusedItem().getFilename();
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

    public void btnDeleteClientOnAction(ActionEvent actionEvent) {
        Path path = Paths.get(currentFolder, listClientFiles.getFocusModel().getFocusedItem().getFilename());
        if(Files.exists(path)){
            try {
                Files.delete(path);
            } catch (IOException e) {
                showMessage(Alert.AlertType.ERROR, "Ошибка", "Ошибка при удалении файла");
                e.printStackTrace();
            }
        }

        updateList(Paths.get(currentFolder));
    }

    public void btnRefreshClientOnAction(ActionEvent actionEvent) {
        updateList(Paths.get(currentFolder));
    }

    public void btnPathUpAction(ActionEvent actionEvent) {
        Path upperPath = Paths.get(pathField.getText()).getParent();
        if(upperPath != null){
            updateList(upperPath);
            currentFolder = upperPath.toString();
        }
    }

    public void selectDiskOnAction(ActionEvent actionEvent) {
        ComboBox<String> element = (ComboBox<String>)actionEvent.getSource();
        updateList(Paths.get(element.getSelectionModel().getSelectedItem()));
        currentFolder = element.getSelectionModel().getSelectedItem();
    }

    public void closeProgram(ActionEvent actionEvent) {
        network.stop();
        Platform.exit();
    }


}
