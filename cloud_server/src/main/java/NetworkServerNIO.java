import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class NetworkServerNIO implements Runnable {
    private int port;
    private String rootFolder;

    private ServerSocketChannel server;
    private Selector selector;
    private ByteBuffer buf = ByteBuffer.allocate(256);
    private int acceptedClientIndex = 1;
    private Map<String, String> clients = new HashMap<>();

    public NetworkServerNIO(int port, String rootFolder) throws IOException {
        this.port = port;
        this.rootFolder = rootFolder;
        this.server = ServerSocketChannel.open();
        this.server.socket().bind(new InetSocketAddress(port));
        this.server.configureBlocking(false);
        this.selector = Selector.open();
        this.server.register(this.selector, SelectionKey.OP_ACCEPT);

    }

    @Override
    public void run() {
        try {
            System.out.println("Сервер был успешно запущен на порту " + port);
            Iterator<SelectionKey> iter;
            SelectionKey key;

            while (server.isOpen()) {
                selector.select();
                iter = this.selector.selectedKeys().iterator();
                while (iter.hasNext()) {
                    key = iter.next();
                    iter.remove();
                    if(key.isAcceptable()){
                        this.handleAccept(key);
                    }
                    if(key.isReadable()){
                        this.handleRead(key);
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void handleAccept(SelectionKey key)  {
        try {
            SocketChannel sc = ((ServerSocketChannel) key.channel()).accept();
            String clientName = "Клиент #" + acceptedClientIndex;
            clients.put(clientName, "");
            acceptedClientIndex++;
            sc.configureBlocking(false);
            sc.register(selector, SelectionKey.OP_READ, clientName);
            System.out.println(clientName + " подключился...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleRead(SelectionKey key)  {

        //if(!key.isConnectable()) return;
        buf.clear();
        try {
            String currentClient = (String)key.attachment();
            StringBuilder command = new StringBuilder();
            SocketChannel ch = (SocketChannel) key.channel();

            int count = 0;
            while ((count = ch.read(buf)) > 0){
                buf.flip();
                byte[] bytes = new byte[buf.limit()];
                buf.get(bytes);
                command.append(new String(bytes));
                buf.clear();
            }

            if (count < 0) {
                System.out.println(currentClient + " отключился\n");
                ch.close();
                return;
            }

            String login = clients.get(currentClient);
            System.out.println(command.toString());
            if(command.toString().startsWith("/login")){
                login = command.toString().replaceFirst("/login", "");
                clients.put(currentClient, login);
                Path path = Paths.get(rootFolder,login);
                if(!Files.exists(path))
                    Files.createDirectory(path);
            }else if(command.toString().startsWith("/download")){
                System.out.println("download" + login);
                String filename = command.toString().replaceFirst("/download", "");
                Path pathfile = Paths.get(rootFolder, login, filename);
                if (Files.exists(pathfile)){
                    long filesize = Files.size(pathfile);
                    LongBuffer lb = buf.asLongBuffer();
                    lb.put(filesize);
                    ch.write(buf);
                    System.out.println(filesize);
                    buf.clear();

                    RandomAccessFile fileDown = new RandomAccessFile(pathfile.toFile(),"rw");
                    try (FileChannel inChannel = fileDown.getChannel()){
                        int bytesRead = inChannel.read(buf);
                        while (bytesRead != -1) {
                            buf.flip();
                            while(buf.hasRemaining()){
                                ch.write(buf);
                            }
                            buf.clear();
                            bytesRead = inChannel.read(buf);
                        }

                    }

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleDownload(SelectionKey key){

    }

    private void handleUpload(SelectionKey key){

    }
}
