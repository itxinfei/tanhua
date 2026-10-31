const fs = require('fs');
const p = 'xfjy-app/src/pages/match/index.vue';
let t = fs.readFileSync(p, 'utf8');

function mustReplace(oldS, newS, label) {
  const n = t.split(oldS).length - 1;
  if (n !== 1) throw new Error('[' + label + '] expected exactly 1 match, found ' + n);
  t = t.replace(oldS, newS);
  console.log('OK:', label);
}

// 1) remove rewind handler + rewinding ref
mustReplace(
  '/** 撤销进行中标记（防连点） */\nconst rewinding = ref(false);\nasync function handleRewind() {\n  if (rewinding.value) return;\n  if (!lastSwipe.value) {\n    uni.showToast({ title: "暂无可撤销的操作", icon: "none" });\n    return;\n  }\n  if (!matchStore.canRewind()) {\n    uni.showToast({ title: "今日撤销次数已用完（3次/天）", icon: "none" });\n    return;\n  }\n  const { card, action } = lastSwipe.value;\n  rewinding.value = true;\n  try {\n    // 回滚后端操作（like/superlike→unlove）并返还滑动次数\n    await matchStore.undoSwipe(card.id, action);\n    currentCardIndex.value--;\n    matchStore.useRewind();\n    lastSwipe.value = null;\n    uni.showToast({ title: "已撤销", icon: "none" });\n  } catch {\n    uni.showToast({ title: "撤销失败，请重试", icon: "none" });\n  } finally {\n    rewinding.value = false;\n  }\n}\nfunction handleBoost() {',
  'function handleBoost() {',
  'rewind handler'
);

// 2) remove .rewind CSS
mustReplace(
  '    &.rewind {\n      width: 88rpx;\n      height: 88rpx;\n      background: linear-gradient(135deg, var(--color-primary-deep), var(--color-primary));\n      color: #fff;\n      box-shadow: 0 6rpx 20rpx rgba(255, 179, 71, 0.4);\n    }\n\n    &.dislike {',
  '    &.dislike {',
  'rewind css'
);

// 3) remove .superlike CSS
mustReplace(
  '    &.superlike {\n      width: 88rpx;\n      height: 88rpx;\n      background: linear-gradient(135deg, #4cd964, #2ecc71);\n      color: #fff;\n      box-shadow: 0 6rpx 20rpx rgba(76, 217, 100, 0.4);\n    }\n\n    &.like {',
  '    &.like {',
  'superlike css'
);

fs.writeFileSync(p, t);
console.log('written');
