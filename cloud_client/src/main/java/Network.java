import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;



public class Network {
    private String serverName;
    private int serverPort;
    private FileTransfer fileTransfer;
    private AuthTransfer authTransfer;
    private final static Network ourInstance = new Network();
    private Channel currentChannel;

    private Network(String serverName, int serverPort) {
        this.serverName = serverName;
        this.serverPort = serverPort;
        this.fileTransfer = new FileTransfer();
        this.authTransfer = new AuthTransfer();
        new Thread(()-> start()).start();
    }

    private Network() {
        //по возможности загружать настройки из файла типа properties
        this("localhost",8189);
    }

    public FileTransfer getFileTransfer() {
        return fileTransfer;
    }

    public AuthTransfer getAuthTransfer() {
        return authTransfer;
    }

    public static Network getInstance() {
        return ourInstance;
    }

    public Channel getCurrentChannel() {
        return currentChannel;
    }

    public void start() {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap clientBootstrap = new Bootstrap();
            clientBootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .remoteAddress(new InetSocketAddress(serverName, serverPort))
                    .handler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            currentChannel = socketChannel;
                            socketChannel.pipeline().addLast(new ServerHandler(fileTransfer, authTransfer));
                        }
                    });
            ChannelFuture channelFuture = clientBootstrap.connect().sync();
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

    public void stop() {
        currentChannel.close();
    }
}
