import java.sql.*;


public class AuthServer {
    private Connection connection = null;

    public AuthServer() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:cloud_server/src/main/resources/cloud_db.sqlite");
            System.out.println("База подключена");
        } catch (Exception e) {
            System.out.println("Ошибка подключения к базе!");
            e.printStackTrace();
        }
    }

    public void closeConnection(){
        try {
            if(connection!=null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean authSuccess(String login, String password){

        if(login.isEmpty()){
            return false;
        }

        if(connection != null){
            try {
                PreparedStatement statement = connection.prepareStatement("SELECT login FROM users where login = ? and password = ?");
                statement.setString(1, login);
                statement.setString(2, password);
                ResultSet resultSet = statement.executeQuery();
                if(resultSet.next()){
                    return true;
                }else{
                    return false;
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                return false;
            }
        }else {
            return false;
        }

    }

}
