public enum Command {
    NO_COMMAND ((byte)0),
    ERROR_SERVER((byte)99),//+ошибка- байт команды/длина сообщения/сообщение
    AUTHORIZATION((byte)20),//+авторизация- байт команды/длина логина (int)/login/длина пароля(int)/пароль
    SUCCESS_AUTH((byte)21),//+успешная авторизация- байт команды
    GET_FILE_LIST((byte)30),//+запрос списка файлов - байт команды
    RETURN_FILE_LIST((byte)31),//+возврат списка файлов - байт команды/длина сообщения(int)/сообщение

    DOWNLOAD_FILE((byte)40),//загрузка файла с сервера - байт команды/длина имени файла(int)/имя файла
    DOWNLOAD_FILE_PROCESS((byte)41),//выгрузка файла на клиент - байт команды/размер имени файла/имя файла/размер файла(long)/пакет
    DOWNLOAD_SUCCESS((byte)42),//файл успешно выгружен - байт команды/длина имени файла(int)/имя файла
    DOWNLOAD_ERROR((byte)43),//файл выгружен с ошибкой/повторить выгрузку - байт команды/длина имени файла(int)/имя файла

    UPLOAD_FILE((byte)50),//запрос выгрузки файла на сервер - байт команды/размер файла(long)/длина имени файла(int)/имя файла
    UPLOAD_FILE_PROCESS((byte)51),//выгрузка файла на сервер - байт команды/пакет
    UPLOAD_SUCCESS((byte)52),//файл успешно выгружен - байт команды/длина имени файла(int)/имя файла

    DELETE_FILE((byte)60),//удаление файла на сервере - байт команды/длина имени файла(int)/имя файла
    DELETE_SUCCESS((byte)61),//файл успешно удален - байт команды/размер файла(long)/пакет
    ;

    private byte commandCode;

    Command(byte commandCode) {
        this.commandCode = commandCode;
    }

    public byte getCommandCode() {
        return commandCode;
    }
}
