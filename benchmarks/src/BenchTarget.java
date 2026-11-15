import java.util.*;

/** Java side of the interop benchmarks, shaped like typical game classes that mods touch. */
public class BenchTarget{
    public static final int SIZE = 8;
    public static int staticCounter;

    public float x, y;
    public int counter;
    public String name = "target";
    public float[] values = new float[64];
    public ArrayList<Object> list = new ArrayList<>();
    public Mode mode = Mode.a;
    public static BenchTarget[] items = new BenchTarget[64];

    static{
        for(int i = 0; i < items.length; i++){
            items[i] = new BenchTarget();
            items[i].x = i;
        }
    }

    public static float clamp(float value, float min, float max){
        return Math.max(min, Math.min(max, value));
    }

    public static float dst(float x1, float y1, float x2, float y2){
        float dx = x2 - x1, dy = y2 - y1;
        return (float)Math.sqrt(dx * dx + dy * dy);
    }

    public void add(int amount){
        counter += amount;
    }

    public int over(int value){
        return value;
    }

    public int over(String value){
        return value.length();
    }

    public int over(float a, float b){
        return (int)(a + b);
    }

    public BenchTarget set(float x, float y){
        this.x = x;
        this.y = y;
        return this;
    }

    public String getLabel(){
        return name;
    }

    public static float sum(float... values){
        float sum = 0;
        for(float f : values) sum += f;
        return sum;
    }

    public static <T> void each(T[] array, Cons<T> cons){
        for(T t : array) cons.get(t);
    }

    /** @return this object as its interface type, like most game code sees entities */
    public Positioned positioned(){
        return new Point(x, y);
    }

    public static int callRunnable(Runnable run, int times){
        for(int i = 0; i < times; i++) run.run();
        return times;
    }

    public static float callListener(Listener listener, int times){
        float sum = 0;
        for(int i = 0; i < times; i++) sum += listener.get(i);
        return sum;
    }

    public static int updateAll(Base base, int times){
        for(int i = 0; i < times; i++) base.update();
        return base.ticks;
    }

    public enum Mode{
        a, b, c
    }

    public interface Cons<T>{
        void get(T t);
    }

    public interface Positioned{
        float getX();

        float dst(float x, float y);
    }

    public static class Point implements Positioned{
        final float x, y;

        Point(float x, float y){
            this.x = x;
            this.y = y;
        }

        @Override
        public float getX(){
            return x;
        }

        @Override
        public float dst(float x, float y){
            float dx = this.x - x, dy = this.y - y;
            return (float)Math.sqrt(dx * dx + dy * dy);
        }
    }

    public interface Listener{
        float get(int index);
    }

    public static class Base{
        public int ticks;
        public float health = 100f;

        public void update(){
            ticks++;
        }

        public float damage(float amount){
            return health -= amount;
        }
    }
}
