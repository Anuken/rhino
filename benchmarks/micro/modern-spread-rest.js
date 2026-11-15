bench("spread-rest", function(){
    const f = (...args) => args.length;
    let s = 0;
    const arr = [1, 2, 3];
    for(let i = 0; i < 1000; i++) s += f(...arr, i);
    return s;
});
