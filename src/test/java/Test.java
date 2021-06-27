import it.auties.reified.annotation.Reified;

public class Test {
    public static void main(String[] args) {
        hello();
    }

    private static <@Reified T> void hello(){

    }
}
