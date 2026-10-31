const fs = require("fs");
const obj = JSON.parse(fs.readFileSync("src/pages.json", "utf8"));
console.log("valid JSON OK");
console.log("pages count =", obj.pages.length);
console.log("pages has chat/index =", obj.pages.some(p => p.path === "pages/chat/index"));
console.log("tabBar.list length =", obj.tabBar.list.length, "(expect 4)");
console.log("tabBar has chat =", obj.tabBar.list.some(t => t.pagePath === "pages/chat/index"));
console.log("midButton =", !!obj.tabBar.midButton, obj.tabBar.midButton && obj.tabBar.midButton.iconPath);
console.log("match entry present =", obj.pages[0].path === "pages/match/index");
