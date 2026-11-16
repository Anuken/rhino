import rhino.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/**
 * Standalone benchmark runner that only uses long-stable Rhino APIs, so the same source can run against older builds for comparison.
 * Usage: Bench [-mode interpreted|compiled|both] [-rounds n] [-time ms] [-filter text] [suite...]
 * Suites: v8, sunspider, micro, interop. Prints one "suite/name mode value unit" line per result.
 */
public class Bench{
    static File root = new File(System.getProperty("bench.root", "benchmarks"));
    static int rounds = 3;
    static int minTime = 1000;
    static String filter = null;

    public static void main(String[] args) throws Exception{
        List<String> suites = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        levels.add(-1);
        levels.add(9);

        for(int i = 0; i < args.length; i++){
            switch(args[i]){
                case "-mode":
                    String mode = args[++i];
                    levels.clear();
                    if(!mode.equals("compiled")) levels.add(-1);
                    if(!mode.equals("interpreted")) levels.add(9);
                    break;
                case "-rounds": rounds = Integer.parseInt(args[++i]); break;
                case "-time": minTime = Integer.parseInt(args[++i]); break;
                case "-filter": filter = args[++i]; break;
                default: suites.add(args[i]);
            }
        }
        if(suites.isEmpty()) suites.addAll(Arrays.asList("v8", "sunspider", "micro", "interop"));

        for(int level : levels){
            String mode = level < 0 ? "interpreted" : "compiled";
            for(String suite : suites){
                switch(suite){
                    case "v8": runV8(level, mode); break;
                    case "sunspider": runSunSpider(level, mode); break;
                    case "micro": case "interop": runMicro(suite, level, mode); break;
                    default: throw new IllegalArgumentException("Unknown suite: " + suite);
                }
            }
        }
    }

    static Context enter(int level){
        Context cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_ES6);
        cx.setOptimizationLevel(level);
        return cx;
    }

    static String read(File file) throws IOException{
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    static void eval(Context cx, VarScope scope, File file) throws IOException{
        cx.evaluateString(scope, read(file), file.getName(), 1);
    }

    static boolean skip(String name){
        return filter != null && !name.contains(filter);
    }

    static void report(String suite, String name, String mode, double value, String unit){
        System.out.printf(Locale.ROOT, "%s/%s %s %.2f %s%n", suite, name, mode, value, unit);
    }

    static void runV8(int level, String mode) throws IOException{
        String[] files = {"richards", "deltablue", "crypto", "raytrace", "earley-boyer", "regexp", "splay"};
        File dir = new File(root, "v8");
        double logSum = 0;
        int count = 0;
        for(String file : files){
            if(skip(file)) continue;
            //each suite gets a fresh scope, so one failing suite can't affect the others
            Context cx = enter(level);
            try{
                TopLevel scope = cx.initStandardObjects();
                eval(cx, scope, new File(dir, "base.js"));
                eval(cx, scope, new File(dir, file + ".js"));
                ScriptableObject.putProperty(scope, "MIN_TIME", minTime);
                Map<String, Double> results = new LinkedHashMap<>();
                ScriptableObject.putProperty(scope, "results", Context.javaToJS(results, scope));
                for(int r = 0; r < rounds; r++){
                    results.clear();
                    cx.evaluateString(
                            scope,
                            // raw scores; older builds format numbers incorrectly
                            "BenchmarkSuite.FormatScore = function(value){ return value; };"
                                    + "BenchmarkSuite.RunSuites({"
                                    + "  NotifyResult: function(name, result){ results.put(name, new java.lang.Double(result)); },"
                                    + "  NotifyError: function(name, error){ throw new Error(name + ': ' + error); }"
                                    + "});",
                            "run.js",
                            1);
                }
                for(Map.Entry<String, Double> e : results.entrySet()){
                    report("v8", e.getKey(), mode, e.getValue(), "score");
                    logSum += Math.log(e.getValue());
                    count++;
                }
            }catch(RuntimeException e){
                System.out.println("v8/" + file + " " + mode + " failed: " + String.valueOf(e.getMessage()).split("\n")[0]);
            }finally{
                Context.exit();
            }
        }
        if(count == files.length) report("v8", "Score", mode, Math.exp(logSum / count), "score");
    }

    static void runSunSpider(int level, String mode) throws IOException{
        File dir = new File(root, "sunspider");
        double total = 0;
        boolean failed = false;
        for(String name : read(new File(dir, "LIST")).split("\\s+")){
            if(name.isEmpty() || skip(name)) continue;
            String source = read(new File(dir, name + ".js"));
            Context cx = enter(level);
            try{
                Script script = cx.compileString(source, name + ".js", 1);
                //warm up for a bit, then time fresh-scope runs
                double best = Double.MAX_VALUE;
                long end = System.currentTimeMillis() + minTime;
                int runs = 0;
                for(int r = 0; r < rounds || System.currentTimeMillis() < end; r++){
                    TopLevel scope = cx.initStandardObjects();
                    long start = System.nanoTime();
                    script.exec(cx, scope);
                    double ms = (System.nanoTime() - start) / 1e6;
                    if(r >= rounds / 2) best = Math.min(best, ms);
                    runs++;
                }
                total += best;
                report("sunspider", name, mode, best, "ms");
            }catch(RuntimeException e){
                failed = true;
                System.out.println("sunspider/" + name + " " + mode + " failed: " + String.valueOf(e.getMessage()).split("\n")[0]);
            }finally{
                Context.exit();
            }
        }
        if(filter == null && !failed) report("sunspider", "Total", mode, total, "ms");
    }

    /** Micro suites register benchmarks with bench(name, fn); each fn performs a fixed amount of work. */
    static void runMicro(String suite, int level, String mode) throws IOException{
        File dir = new File(root, suite);
        File[] files = dir.listFiles((d, n) -> n.endsWith(".js"));
        Arrays.sort(files);
        Context cx = enter(level);
        try{
            //return Java strings and numbers as JS values, the default of older builds
            cx.getWrapFactory().setJavaPrimitiveWrap(false);
            ImporterTopLevel scope = new ImporterTopLevel(cx);
            scope.put("BenchTarget", scope, new NativeJavaClass(scope, BenchTarget.class));
            cx.evaluateString(
                    scope,
                    "var benchmarks = []; function bench(name, fn){ benchmarks.push({name: name, fn: fn}); }",
                    "bench.js",
                    1);
            for(File file : files){
                try{
                    eval(cx, scope, file);
                }catch(RuntimeException e){
                    System.out.println(suite + "/" + file.getName() + " " + mode + " unsupported: " + String.valueOf(e.getMessage()).split("\n")[0]);
                }
            }

            NativeArray list = (NativeArray)scope.get("benchmarks", scope);
            for(int i = 0; i < (int)list.getLength(); i++){
                Scriptable entry = (Scriptable)list.get(i, list);
                String name = Context.toString(entry.get("name", entry));
                if(skip(name)) continue;
                Function fn = (Function)entry.get("fn", entry);
                double best = Double.MAX_VALUE;
                try{
                    fn.call(cx, scope, scope, new Object[0]);
                }catch(RuntimeException e){
                    System.out.println(suite + "/" + name + " " + mode + " failed: " + String.valueOf(e.getMessage()).split("\n")[0]);
                    continue;
                }
                for(int r = 0; r < rounds; r++){
                    long start = System.nanoTime(), end = start + minTime * 1_000_000L / rounds, now;
                    int runs = 0;
                    do{
                        fn.call(cx, scope, scope, new Object[0]);
                        runs++;
                    }while((now = System.nanoTime()) < end);
                    if(r > 0 || rounds == 1) best = Math.min(best, (now - start) / 1e3 / runs);
                }
                report(suite, name, mode, best, "us");
            }
        }finally{
            Context.exit();
        }
    }
}
