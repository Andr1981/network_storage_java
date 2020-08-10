import java.io.*;
import java.net.Socket;

public class Client {
    private static Socket socket;
    private static String login;

    public static void main(String[] args) {

        DataInputStream in = null;
        DataOutputStream out = null;

        try(BufferedReader readConsole = new BufferedReader(new InputStreamReader(System.in))){
            System.out.println("Input server address");
            String serverName = readConsole.readLine().trim();
            System.out.println("Input server port");
            int serverPort = Integer.parseInt(readConsole.readLine());
            //подключение
            socket = new Socket(serverName, serverPort);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            System.out.println("Input login");
            login = readConsole.readLine().trim();

            //отправка логина
            //out.writeUTF("/login");
            out.write("/login".getBytes());
            //out.writeUTF(login);
            out.write(login.getBytes());

            //бесконечный цикл
            String fileName = null;
            String dirFilename = null;
            while (true){
                System.out.println("Введите команду:");
                System.out.println("Загрузить файл с облака в папку:/download");
                System.out.println("Загрузить файл в облако:/upload");
                System.out.println("Запросить список файлов:/all");
                System.out.println("Завершить программу:/end");
                String temp = readConsole.readLine();

                if(temp.equals("/download")){
                    //отправить команду на сервер
                    //out.writeUTF(temp);
                    out.write(temp.getBytes());

                    System.out.println("Введите имя файла:");
                    fileName = readConsole.readLine();
                    //отправить имя файл, который нужно скачать
                    //out.writeUTF(fileName);
                    out.write(fileName.getBytes());

                    System.out.println("Введите имя директории, куда поместить файл:");
                    dirFilename = readConsole.readLine();

                    long fileSize = in.readLong();
                    System.out.println(fileSize);
                    //получаем файл и грузим в директорию
                    File dirFile = FileUtility.createDirectory(dirFilename);
                    File loadFile = FileUtility.createFile(dirFile.getAbsolutePath()+"/"+fileName);
                    try (FileOutputStream inFile = new FileOutputStream(loadFile)){
                        byte[] bufferIn = new byte[8192];
                        long getCount = 0l;
                        while(fileSize > getCount){
                            int count = in.read(bufferIn);
                            if(count <= 0) break;
                            getCount = getCount + count;
                            inFile.write(bufferIn, 0, count);
                        }
                        inFile.flush();
                    }catch (IOException e){
                        System.out.println("Ошибка загрузки файла");
                        e.printStackTrace();
                    }

                    System.out.println("Загрузка завершена");

                }else if(temp.equals("/upload")){
                    //отправить команду на сервер
                    out.writeUTF(temp);

                    System.out.println("Введите полное имя файла:");
                    fileName = readConsole.readLine();
                    File sendFile = FileUtility.createFile(fileName);
                    //отправить имя файла на сервер
                    out.writeUTF(sendFile.getName());

                    long fileSize = sendFile.length();
                    out.writeLong(fileSize);

                    //выгружаем файл в сокет
                    try(FileInputStream outFile = new FileInputStream(sendFile)){
                        byte[] bufferOut = new byte[8192];
                        long getCount = 0l;
                        while(fileSize > getCount){
                            int count = outFile.read(bufferOut);
                            if(count <= 0) break;
                            getCount = getCount + count;
                            out.write(bufferOut, 0, count);
                        }
                        out.flush();
                    }catch (IOException e){
                        System.out.println("Ошибка выгрузки файла");
                        e.printStackTrace();
                    }
                    System.out.println("Выгрузка завершена");

                }else if(temp.equals("/all")){
                    //отправить команду на сервер
                    out.writeUTF(temp);

                    //получаем ответ
                    System.out.println("Список файлов");
                    while (true){
                        //выводим многострочный список
                        String fileTemp = in.readUTF();
                        if(fileTemp.equals("/end_list")) break;
                        System.out.println("  " + fileTemp);
                    }
                    System.out.println("_____________");


                }else if(temp.equals("/end")){
                    //закрытие
                    System.out.println("Bye bye");
                    break;
                }
            }

        }catch (IOException e){
            e.printStackTrace();
        }finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
