package rhino.tests.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.command.dexer.DxContext;
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.cf.CfOptions;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.file.DexFile;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rhino.Context;
import rhino.ContextFactory;
import rhino.DefiningClassLoader;
import rhino.GeneratedClassLoader;

/**
 * Without invokedynamic ({@code rhino.indy=false}, as on Android), compiled scripts and JavaAdapter
 * classes must not need java.lang.invoke: the old dx library that converts them to dex at runtime
 * can't handle invokedynamic or method handle constants. Run by the testNoIndy task.
 */
public class PortableBytecodeTest {
    private static final int CONSTANT_METHOD_HANDLE = 15;
    private static final int CONSTANT_METHOD_TYPE = 16;
    private static final int CONSTANT_DYNAMIC = 17;
    private static final int CONSTANT_INVOKE_DYNAMIC = 18;

    private static Map<String, byte[]> classes;

    @BeforeAll
    static void compile() {
        assumeTrue(
                "false".equals(System.getProperty("rhino.indy")),
                "only meaningful with -Drhino.indy=false");
        String source = buildScript();
        Map<String, byte[]> captured = new LinkedHashMap<>();
        Object compiled = run(source, false, captured);
        Object interpreted = run(source, true, new LinkedHashMap<>());
        assertEquals(interpreted, compiled);
        assertTrue(
                captured.keySet().stream().anyMatch(n -> n.contains("$part")),
                "expected the script to be split across classes: " + captured.keySet());
        assertTrue(
                captured.keySet().stream().anyMatch(n -> n.contains("JavaAdapter")),
                "expected a JavaAdapter class: " + captured.keySet());
        classes = captured;
    }

    @Test
    void constantPoolsHaveNoInvokeDynamicOrMethodHandles() throws IOException {
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            checkConstantPool(e.getKey(), e.getValue());
        }
    }

    @Test
    void dxTranslatesGeneratedClasses() {
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            // On Android ClassFileWriter can't read its own class file and falls back to 48.0.
            byte[] data = e.getValue().clone();
            data[6] = 0;
            data[7] = 48;

            DexOptions dexOptions = new DexOptions();
            DexFile dexFile = new DexFile(dexOptions);
            DirectClassFile classFile =
                    new DirectClassFile(data, e.getKey().replace('.', '/') + ".class", true);
            classFile.setAttributeFactory(StdAttributeFactory.THE_ONE);
            classFile.getMagic();
            dexFile.add(
                    CfTranslator.translate(
                            new DxContext(),
                            classFile,
                            null,
                            new CfOptions(),
                            dexOptions,
                            dexFile));
            try {
                dexFile.toDex(null, false);
            } catch (IOException x) {
                throw new AssertionError(e.getKey(), x);
            }
        }
    }

    @Test
    void d8DexesGeneratedClassesForApi21(@TempDir Path dir) throws Exception {
        File sdk = androidSdk();
        File d8 = newest(new File(sdk, "build-tools"), "d8");
        File androidJar = newest(new File(sdk, "platforms"), "android.jar");
        assumeTrue(d8 != null && androidJar != null, "Android SDK with d8 not found");

        List<String> command = new ArrayList<>();
        command.add(d8.getPath());
        command.add("--min-api");
        command.add("21");
        command.add("--lib");
        command.add(androidJar.getPath());
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (entry.replace('\\', '/').endsWith("classes/java/main")) {
                command.add("--classpath");
                command.add(entry);
            }
        }
        Path out = Files.createDirectories(dir.resolve("out"));
        command.add("--output");
        command.add(out.toString());
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            Path file = dir.resolve("in").resolve(e.getKey().replace('.', '/') + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, e.getValue());
            command.add(file.toString());
        }

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        assertTrue(Files.exists(out.resolve("classes.dex")), output);
    }

    private static Object run(String source, boolean interpreted, Map<String, byte[]> captured) {
        ContextFactory factory =
                new ContextFactory() {
                    @Override
                    protected GeneratedClassLoader createClassLoader(ClassLoader parent) {
                        return new DefiningClassLoader(parent) {
                            @Override
                            public Class<?> defineClass(String name, byte[] data) {
                                captured.put(name, data);
                                return super.defineClass(name, data);
                            }
                        };
                    }
                };
        try (Context cx = factory.enterContext()) {
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.setInterpretedMode(interpreted);
            return Context.toString(
                    cx.evaluateString(cx.initStandardObjects(), source, "portable.js", 1, null));
        }
    }

    private static String buildScript() {
        StringBuilder src = new StringBuilder();
        src.append(
                "const K = 3;\n"
                    + "function makeCounter(n) { return { inc: () => ++n, get value() { return n; }"
                    + " }; }\n"
                    + "function* range(a, b) { for (let i = a; i < b; i++) yield i * K; }\n"
                    + "function Shape(w, h) { this.w = w; this.h = h; }\n"
                    + "Shape.prototype.area = function() { return this.w * this.h; };\n"
                    + "Shape.of = s => new Shape(s, s);\n"
                    + "var base = { area() { return 10; } };\n"
                    + "var derived = { __proto__: base, area() { return super.area() + 1; } };\n"
                    + "class Point { #x; constructor(x) { this.#x = x; } get x() { return this.#x;"
                    + " } static of(...a) { return new this(...a); } }\n"
                    + "class Point3 extends Point { constructor(x) { super(x * 3); } }\n"
                    + "function tag(strings, ...vals) { return strings.raw.join('|') +"
                    + " vals.join(','); }\n");
        // Enough distinct string constants to overflow one class file's constant pool.
        int functions = 400;
        for (int f = 0; f < functions; f++) {
            src.append("function big").append(f).append("() { return [");
            for (int i = 0; i < 90; i++) {
                src.append("'s").append(f).append('_').append(i).append("',");
            }
            src.append("]; }\n");
        }
        src.append(
                "function* tail(x) { const y = yield x; yield `${x}+${y}`; }\n"
                    + "var c = makeCounter(1); c.inc(); c.inc();\n"
                    + "var sum = 0; for (const v of range(0, 4)) sum += v;\n"
                    + "var m = 'a 12-34 b 5-6'.replace(/(\\d+)-(\\d+)/g, (all, x, y) => y + x);\n"
                    + "var {w, ...rest} = {w: 2, h: 5, d: 1};\n"
                    + "var arr = [1, 2, 3].map(x => x * 2); arr = [...arr, 7.5, 8];\n"
                    + "var o = {a: {b: null}}; var opt = o.a?.b?.c ?? 'none';\n"
                    + "var s = ''; for (var i = 0; i < 5; i++) { s += i; if (i > 2.5) s += '!'; }\n"
                    + "var eq = (1 == 1.0) && ('x' === 'x') && !(true != true) && 2 >= 1.5;\n"
                    + "var err; try { null.x; } catch (e) { err = e instanceof TypeError; }\n"
                    + "var sw; switch (arr.length) { case 5: sw = 'five'; break; default: sw ="
                    + " 'other'; }\n"
                    + "var t = tail(4); t.next(); var tv = t.next(5).value;\n"
                    + "var adapted = new JavaAdapter(java.util.ArrayList, java.lang.Runnable, {"
                    + " size: function() { return 42; }, run: function() {} });\n"
                    + "var bigs = big0().length + big"
                        + (functions - 1)
                        + "()[89];\n"
                        + "[c.value, sum, m, w, Object.keys(rest).join(''), arr.join(','), opt, s,"
                        + " eq, err, sw, derived.area(), Shape.of(2).area(), tag`a${1}b${2}c`, tv,"
                        + " bigs, K + arr.length, ~~(-7 / 2) >>> 0, adapted.size(),"
                        + " Point3.of(...[2]).x, Math.max(...arr)].join(';');\n");
        return src.toString();
    }

    private static void checkConstantPool(String name, byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        assertEquals(0xCAFEBABE, in.readInt(), name);
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1:
                    String utf = in.readUTF();
                    assertTrue(!utf.contains("java/lang/invoke"), name + " references " + utf);
                    break;
                case 3:
                case 4:
                    in.readInt();
                    break;
                case 5:
                case 6:
                    in.readLong();
                    i++;
                    break;
                case 7:
                case 8:
                case 19:
                case 20:
                    in.readUnsignedShort();
                    break;
                case 9:
                case 10:
                case 11:
                case 12:
                    in.readInt();
                    break;
                case CONSTANT_METHOD_HANDLE:
                case CONSTANT_METHOD_TYPE:
                case CONSTANT_DYNAMIC:
                case CONSTANT_INVOKE_DYNAMIC:
                    fail(name + " has constant pool tag " + tag);
                    break;
                default:
                    fail(name + " has unknown constant pool tag " + tag);
            }
        }
    }

    private static File androidSdk() {
        for (String var : Arrays.asList("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
            String path = System.getenv(var);
            if (path != null && new File(path).isDirectory()) {
                return new File(path);
            }
        }
        return new File(System.getProperty("user.home"), "Android");
    }

    /** The given file in the last (by version name) subdirectory of dir that has it. */
    private static File newest(File dir, String fileName) {
        File[] versions = dir.listFiles(File::isDirectory);
        if (versions == null) {
            return null;
        }
        Arrays.sort(versions);
        for (int i = versions.length - 1; i >= 0; i--) {
            File file = new File(versions[i], fileName);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }
}
