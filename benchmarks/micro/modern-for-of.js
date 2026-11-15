bench("for-of", function(){
    const arr = [];
    for(let i = 0; i < 1000; i++) arr.push(i);
    let s = 0;
    for(const v of arr) s += v;
    return s;
});
