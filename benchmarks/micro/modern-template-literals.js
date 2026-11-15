bench("template-literals", function(){
    let n = 0;
    for(let i = 0; i < 1000; i++) n += `item ${i} of ${n & 7}`.length;
    return n;
});
