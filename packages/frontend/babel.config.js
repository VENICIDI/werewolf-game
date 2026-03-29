// babel-preset-taro 会根据 Taro 版本自动引入 TypeScript / React 相关 Babel 插件
module.exports = {
  presets: [
    ['taro', {
      framework: 'react',
      ts: true,
    }]
  ]
}
