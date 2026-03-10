export default defineAppConfig({
  pages: [
    'pages/index/index',
    'pages/login/index',
    'pages/register/index',
    'pages/room-list/index',
    'pages/room/index',
    'pages/game/index',
    'pages/profile/index'
  ],
  window: {
    backgroundTextStyle: 'light',
    navigationBarBackgroundColor: '#1a1a2e',
    navigationBarTitleText: '狼人杀',
    navigationBarTextStyle: 'white'
  },
  tabBar: {
    color: '#999',
    selectedColor: '#e94560',
    backgroundColor: '#1a1a2e',
    borderStyle: 'black',
    list: [
      {
        pagePath: 'pages/index/index',
        text: '首页',
        iconPath: 'assets/images/home.png',
        selectedIconPath: 'assets/images/home-active.png'
      },
      {
        pagePath: 'pages/room-list/index',
        text: '房间',
        iconPath: 'assets/images/room.png',
        selectedIconPath: 'assets/images/room-active.png'
      },
      {
        pagePath: 'pages/profile/index',
        text: '我的',
        iconPath: 'assets/images/profile.png',
        selectedIconPath: 'assets/images/profile-active.png'
      }
    ]
  }
})
