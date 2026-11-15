bench("optional-chaining", function(){
    const o = {a: {b: {c: 1}}};
    let s = 0;
    for(let i = 0; i < 5000; i++) s += (o?.a?.b?.c ?? 0) + (o.x?.y ?? 1);
    return s;
});
