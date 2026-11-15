//Java interop micro-benchmarks, modeled on what game mods do every frame

var target = new BenchTarget();

bench("instance-method", function(){
    for(var i = 0; i < 2000; i++) target.add(1);
    return target.counter;
});

bench("static-method", function(){
    var s = 0;
    for(var i = 0; i < 2000; i++) s += BenchTarget.clamp(i / 1000, 0, 1);
    return s;
});

bench("static-method-4args", function(){
    var s = 0;
    for(var i = 0; i < 2000; i++) s += BenchTarget.dst(0, 0, i, i);
    return s;
});

bench("overloaded-method", function(){
    var s = 0;
    for(var i = 0; i < 1000; i++) s += target.over(i) + target.over("abc") + target.over(i, 2);
    return s;
});

bench("field-read-write", function(){
    for(var i = 0; i < 2000; i++){
        target.x = target.x + 1;
        target.y += target.x;
    }
    return target.y;
});

bench("bean-property", function(){
    var n = 0;
    for(var i = 0; i < 2000; i++) n += target.label.length;
    return n;
});

bench("chained-calls", function(){
    var t = target;
    for(var i = 0; i < 1000; i++) t = t.set(i, i).set(i + 1, i);
    return t.x;
});

bench("java-array", function(){
    var arr = target.values, s = 0;
    for(var i = 0; i < 2000; i++){
        arr[i & 63] = i;
        s += arr[(i + 1) & 63];
    }
    return s;
});

bench("java-list", function(){
    var list = target.list;
    list.clear();
    for(var i = 0; i < 500; i++) list.add(i);
    var s = 0;
    for(var j = 0; j < list.size(); j++) s += list.get(j);
    return s;
});

bench("static-field", function(){
    for(var i = 0; i < 2000; i++) BenchTarget.staticCounter++;
    return BenchTarget.staticCounter;
});

bench("js-runnable-from-java", function(){
    var n = 0;
    BenchTarget.callRunnable(function(){ n++; }, 2000);
    return n;
});

bench("js-interface-from-java", function(){
    return BenchTarget.callListener(function(i){ return i * 0.5; }, 2000);
});

var adapter = new JavaAdapter(BenchTarget.Base, {
    update: function(){
        this.super$update();
        this.health = this.health - 0.01;
    }
});

bench("adapter-override", function(){
    return BenchTarget.updateAll(adapter, 1000);
});

bench("new-java-object", function(){
    var last;
    for(var i = 0; i < 500; i++) last = new BenchTarget();
    return last;
});

bench("java-string", function(){
    var s = new java.lang.StringBuilder();
    for(var i = 0; i < 500; i++) s.append("a").append(i);
    return s.toString().length;
});

bench("varargs", function(){
    var s = 0;
    for(var i = 0; i < 1000; i++) s += BenchTarget.sum(i, 1, 2);
    return s;
});

bench("function-arg-per-call", function(){
    var s = 0;
    for(var i = 0; i < 500; i++) s += BenchTarget.callListener(function(j){ return j + i; }, 1);
    return s;
});

var sameListener = function(j){ return j; };
bench("function-arg-reused", function(){
    var s = 0;
    for(var i = 0; i < 1000; i++) s += BenchTarget.callListener(sameListener, 1);
    return s;
});

bench("each-callback", function(){
    var s = 0;
    for(var i = 0; i < 10; i++) BenchTarget.each(BenchTarget.items, function(t){ s += t.x; });
    return s;
});

bench("object-array", function(){
    var items = BenchTarget.items, s = 0;
    for(var r = 0; r < 10; r++){
        for(var i = 0; i < items.length; i++) s += items[i].x;
    }
    return s;
});

bench("static-constant", function(){
    var s = 0;
    for(var i = 0; i < 2000; i++) s += BenchTarget.SIZE;
    return s;
});

bench("instanceof", function(){
    var n = 0, items = BenchTarget.items;
    for(var i = 0; i < 1000; i++) if(items[i & 63] instanceof BenchTarget) n++;
    return n;
});

bench("string-concat", function(){
    var n = 0;
    for(var i = 0; i < 1000; i++) n += ("unit-" + target.name + i).length;
    return n;
});

bench("enum", function(){
    var n = 0, Mode = BenchTarget.Mode;
    for(var i = 0; i < 1000; i++){
        target.mode = (i & 1) == 0 ? Mode.a : Mode.b;
        if(target.mode == Mode.b) n++;
    }
    return n;
});

bench("interface-typed", function(){
    var p = target.positioned(), s = 0;
    for(var i = 0; i < 1000; i++) s += p.dst(i, 0) + p.getX();
    return s;
});
