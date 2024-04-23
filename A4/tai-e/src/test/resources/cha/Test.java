interface Number {
    int get();
}

public class Test {

    public static void main(String[] args) {
        Number n = new One();
        n.get();
    }
}

class Zero implements Number {

    public int get() {
        return 0;
    }
}

class One implements Number {

    public int get() {
        return 1;
    }
}

class Two implements Number {

    public int get() {
        return 2;
    }
}

class Three extends Two {
    @Override
    public int get() {
        return 3;
    }
}