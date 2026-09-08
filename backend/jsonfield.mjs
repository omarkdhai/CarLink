import fs from 'fs';
const [field] = process.argv.slice(2);
const data = JSON.parse(fs.readFileSync(0, 'utf8'));
function get(obj, path) { return path.split('.').reduce((o,k)=>o==null?undefined:o[k], obj); }
const val = get(data, field);
if (val !== undefined) { if (typeof val === 'object') console.log(JSON.stringify(val)); else console.log(val); }
