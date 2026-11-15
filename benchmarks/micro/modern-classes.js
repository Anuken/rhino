bench("classes", function(){
    class P{ constructor(x){ this.x = x; } get double(){ return this.x * 2; } }
    class Q extends P{ constructor(x){ super(x); this.y = 1; } }
    let s = 0;
    for(let i = 0; i < 1000; i++) s += new Q(i).double;
    return s;
});
