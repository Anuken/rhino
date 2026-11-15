//core language micro-benchmarks; each function does a fixed amount of work

bench("property-access", function(){
    var o = {a: 1, b: 2, c: 3, d: 4};
    var sum = 0;
    for(var i = 0; i < 10000; i++){
        sum += o.a + o.b + o.c + o.d;
        o.a = i & 7;
    }
    return sum;
});

bench("object-create", function(){
    var last;
    for(var i = 0; i < 2000; i++){
        last = {x: i, y: i + 1, name: "obj", next: last ? null : last};
    }
    return last;
});

function Vec(x, y){ this.x = x; this.y = y; }
Vec.prototype.add = function(o){ return new Vec(this.x + o.x, this.y + o.y); };
Vec.prototype.len = function(){ return Math.sqrt(this.x * this.x + this.y * this.y); };

bench("prototype-methods", function(){
    var v = new Vec(0, 0), d = new Vec(1, 2), s = 0;
    for(var i = 0; i < 2000; i++){
        v = v.add(d);
        s += v.len();
    }
    return s;
});

bench("closures", function(){
    function make(n){ return function(x){ return x + n; }; }
    var s = 0;
    for(var i = 0; i < 2000; i++){
        s = make(i)(s) & 0xffff;
    }
    return s;
});

bench("arrow-let-const", function(){
    const add = (a, b) => a + b;
    let s = 0;
    for(let i = 0; i < 5000; i++){
        const k = i * 2;
        s = add(s, k) | 0;
    }
    return s;
});

bench("array-push-index", function(){
    var a = [];
    for(var i = 0; i < 5000; i++) a.push(i);
    var s = 0;
    for(var j = 0; j < a.length; j++) s += a[j];
    return s;
});

bench("array-functional", function(){
    var a = [];
    for(var i = 0; i < 500; i++) a.push(i);
    return a.map(function(x){ return x * 2; }).filter(function(x){ return x % 3 == 0; }).reduce(function(p, x){ return p + x; }, 0);
});

bench("array-sort", function(){
    var a = [];
    var seed = 1;
    for(var i = 0; i < 1000; i++){ seed = (seed * 16807) % 2147483647; a.push(seed); }
    a.sort(function(x, y){ return x - y; });
    return a[0];
});

bench("string-concat", function(){
    var s = "";
    for(var i = 0; i < 1000; i++) s += "item" + i + ",";
    return s.length;
});

bench("string-methods", function(){
    var n = 0;
    var str = "the quick brown fox jumps over the lazy dog";
    for(var i = 0; i < 500; i++){
        n += str.indexOf("fox") + str.split(" ").length + str.toUpperCase().charCodeAt(i % 10) + str.substring(4, 9).length;
    }
    return n;
});

bench("math", function(){
    var s = 0;
    for(var i = 0; i < 5000; i++){
        s += Math.sin(i) * Math.floor(i / 3) + Math.max(i, 10) + Math.abs(-i);
    }
    return s;
});

bench("int-arithmetic", function(){
    var s = 0;
    for(var i = 0; i < 20000; i++){
        s = (s + i * 31) | 0;
        s ^= s >>> 3;
    }
    return s;
});

bench("try-catch", function(){
    var n = 0;
    for(var i = 0; i < 200; i++){
        try{ throw new Error("x"); }catch(e){ n++; }
    }
    return n;
});

bench("switch", function(){
    var n = 0;
    for(var i = 0; i < 10000; i++){
        switch(i % 5){
            case 0: n += 1; break;
            case 1: n += 2; break;
            case 2: n -= 1; break;
            default: n += 3;
        }
    }
    return n;
});

bench("map-set", function(){
    var m = new Map(), s = new Set();
    for(var i = 0; i < 1000; i++){ m.set("k" + (i % 100), i); s.add(i % 50); }
    return m.size + s.size + m.get("k5");
});

bench("for-in", function(){
    var o = {a: 1, b: 2, c: 3, d: 4, e: 5, f: 6};
    var n = 0;
    for(var i = 0; i < 500; i++){
        for(var k in o) n += o[k];
    }
    return n;
});

bench("json", function(){
    var o = {name: "test", values: [1, 2, 3, 4, 5], nested: {a: true, b: null, c: "str"}};
    var n = 0;
    for(var i = 0; i < 100; i++) n += JSON.parse(JSON.stringify(o)).values.length;
    return n;
});

bench("regexp", function(){
    var re = /(\w+)@(\w+)\.com/;
    var n = 0;
    for(var i = 0; i < 500; i++){
        var m = re.exec("contact user" + i + "@example.com now");
        if(m) n += m[1].length;
    }
    return n;
});

bench("function-calls", function(){
    function f(a, b, c){ return a + b + c; }
    var s = 0;
    for(var i = 0; i < 10000; i++) s = f(s, i, 1) & 0xffffff;
    return s;
});

bench("arguments", function(){
    function sum(){ var s = 0; for(var j = 0; j < arguments.length; j++) s += arguments[j]; return s; }
    var t = 0;
    for(var i = 0; i < 2000; i++) t += sum(i, 1, 2, 3);
    return t;
});

bench("recursion", function(){
    function fib(n){ return n < 2 ? n : fib(n - 1) + fib(n - 2); }
    return fib(18);
});
