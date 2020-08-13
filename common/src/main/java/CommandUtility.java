public class CommandUtility {
    public static Command getCommand(byte commandByte){
        Command result = Command.NO_COMMAND;
        for (Command cmd:Command.values()) {
            if(cmd.getCommandCode()==commandByte){
                result = cmd;
                break;
            }
        }
        return result;
    }
}
