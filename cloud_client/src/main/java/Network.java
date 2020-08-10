import callback.Callback;
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
    private Channel currentChannel;
    private static Network ourInstance = new Network();
    private Callback authCallBack, getFileListCallBack, downloadFileCallBack, uploadFileCallBack, errorCallBack;

    public void setGetFileListCallBack(Callback getFileListCallBack) {
        this.getFileListCallBack = getFileListCallBack;
    }

    public void setAuthCallBack(Callback authCallBack) {
        this.authCallBack = authCallBack;
    }

    public void setErrorCallBack(Callback errorCallBack) {
        this.errorCallBack = errorCallBack;
    }

    public void setDownloadFileCallBack(Callback downloadFileCallBack) {
        this.downloadFileCallBack = downloadFileCallBack;
    }

    public void setUploadFileCallBack(Callback uploadFileCallBack) {
        this.uploadFileCallBack = uploadFileCallBack;
    }

    private Network(String serverName, int serverPort) {
        this.serverName = serverName;
        this.serverPort = serverPort;
        new Thread(()-> start()).start();
    }

    private Network() {
        //по возможности загружать настройки из файла типа properties
        this("localhost",8189);
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
                            socketChannel.pipeline().addLast(new ServerHandler(authCallBack, getFileListCallBack, downloadFileCallBack, uploadFileCallBack, errorCallBack));
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
