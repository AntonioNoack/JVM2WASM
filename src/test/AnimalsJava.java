package test;

import org.jbox2d.collision.shapes.ShapeType;
import org.jbox2d.dynamics.contacts.ContactRegister;

public class AnimalsJava {
    public static ContactRegister[][] create() {
        int size = ShapeType.values().length;
        return new ContactRegister[size][size];
    }
}
