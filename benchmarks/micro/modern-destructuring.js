bench("destructuring", function(){
    let s = 0;
    for(let i = 0; i < 2000; i++){
        const {a, b} = {a: i, b: 2};
        const [x, y] = [a, b];
        s += x + y;
    }
    return s;
});
