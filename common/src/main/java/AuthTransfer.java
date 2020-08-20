import callback.Callback;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.nio.charset.StandardCharsets;

public class AuthTransfer {
    private int byteLength = 1;
    private int intLength = 4;
    private int longLength = 8;
    private int simpleLength;

    private Callback logInCallback;
    private Callback logOutCallback;

    private String login;
    private String password;

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public void setLogInCallback(Callback logInCallback) {
        this.logInCallback = logInCallback;
    }

    public void setLogOutCallback(Callback logOutCallback) {
        this.logOutCallback = logOutCallback;
    }

    public void callLogInCallback(){
        if(logInCallback != null){
            logInCallback.call();
        }
    }

    public ByteBuf requestSingIn(String login, String password){

        byte[] loginBytes = login.getBytes(StandardCharsets.UTF_8);
        byte[] passBytes = password.getBytes(StandardCharsets.UTF_8);

        ByteBuf buff = ByteBufAllocator.DEFAULT.directBuffer(byteLength+intLength+loginBytes.length+intLength+passBytes.length);
        buff.writeByte(Command.AUTHORIZATION.getCommandCode());
        buff.writeInt(loginBytes.length);
        buff.writeBytes(loginBytes);
        buff.writeInt(passBytes.length);
        buff.writeBytes(passBytes);
        return buff;

    }

    public JobStage readLoginPassword(ByteBuf buff, JobStage currentStage){

        if(currentStage == JobStage.GET_LENGTH_LOGIN){
            if (buff.readableBytes() >= intLength) {
                simpleLength = buff.readInt();
                currentStage = JobStage.GET_LOGIN;
            }
        }

        if(currentStage == JobStage.GET_LOGIN){
            if(buff.readableBytes()>=simpleLength){
                byte[] loginByte = new byte[simpleLength];
                buff.readBytes(loginByte);
                login = new String(loginByte, StandardCharsets.UTF_8);
                currentStage = JobStage.GET_LENGTH_PASS;
            }
        }

        if(currentStage == JobStage.GET_LENGTH_PASS){
            if(buff.readableBytes()>=intLength){
                simpleLength = buff.readInt();
                currentStage = JobStage.GET_PASS;
            }
        }

        if(currentStage == JobStage.GET_PASS) {
            if (buff.readableBytes() >= simpleLength) {
                byte[] passByte = new byte[simpleLength];
                buff.readBytes(passByte);
                password = new String(passByte, StandardCharsets.UTF_8);
                currentStage = JobStage.LOG_IN;
            }
        }

        return currentStage;
    }

    public ByteBuf sendAuthSuccess(){
        ByteBuf answer = ByteBufAllocator.DEFAULT.directBuffer(byteLength);
        answer.writeByte(Command.SUCCESS_AUTH.getCommandCode());
        return answer;
    }
}
