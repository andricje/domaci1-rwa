package serialization;

import com.google.gson.Gson;

public class SerializationMain {

    public static void main(String[] args) {

        String jsonUser = "{\"firstName\":\"Student\",\n\"lastName\":\"Studentic\"}";
        Gson gson = new Gson();
        User user = gson.fromJson(jsonUser,User.class);
        user.setFirstName("Master");
        System.out.println(gson.toJson(user));

    }

}
