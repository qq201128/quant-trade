const { app, BrowserWindow, ipcMain, Menu } = require('electron');
const path = require('path');
const fs = require('fs');

let mainWindow;

// 判断是否为开发环境（必须在 app.whenReady() 之前可用）
function isDevelopment() {
  // 检查命令行参数
  if (process.argv.includes('--dev')) {
    return true;
  }
  // 检查环境变量
  if (process.env.NODE_ENV === 'development' || process.env.ELECTRON_IS_DEV === '1') {
    return true;
  }
  // 检查是否在开发模式下运行（非打包版本）
  if (!app.isPackaged) {
    return true;
  }
  return false;
}

// 读取配置文件
function loadConfig() {
  // 打包后的应用，配置文件可能在 asar 外部或内部
  // 优先尝试 asar 外部（用户可修改），其次尝试 asar 内部
  const possiblePaths = [
    path.join(process.resourcesPath, 'config.json'), // 打包后 asar 外部
    path.join(__dirname, 'config.json'), // 开发环境或 asar 内部
    path.join(app.getAppPath(), 'config.json'), // 应用路径
  ];
  
  for (const configPath of possiblePaths) {
    try {
      if (fs.existsSync(configPath)) {
        const configData = fs.readFileSync(configPath, 'utf8');
        const config = JSON.parse(configData);
        console.log('成功加载配置文件:', configPath);
        return config;
      }
    } catch (error) {
      console.warn('读取配置文件失败:', configPath, error.message);
    }
  }
  
  // 默认配置
  console.log('使用默认配置');
  return {
    backend: {
      dev: 'http://localhost:8080',
      prod: 'http://188.239.21.115:8080'
    }
  };
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 1200,
    minHeight: 800,
    title: '量化交易系统',  // 设置窗口标题为中文
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.js'),
      // 禁用拖放功能，避免 dragEvent 相关错误
      enableWebSQL: false,
      enableRemoteModule: false
    },
    icon: path.join(__dirname, 'assets', 'icon.png'),
    titleBarStyle: 'default',
    show: false
  });
  
  // 禁用窗口拖放，避免 dragEvent 相关错误
  mainWindow.webContents.on('will-navigate', (event) => {
    event.preventDefault();
  });
  
  // 捕获控制台错误，过滤 dragEvent 相关错误
  mainWindow.webContents.on('console-message', (event, level, message) => {
    if (message && message.includes('dragEvent is not defined')) {
      // 忽略这个错误
      return;
    }
  });

  // 加载页面
  mainWindow.loadFile('index.html');

  // 窗口准备好后显示
  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    
    // 开发模式下自动打开开发者工具
    if (isDevelopment()) {
      mainWindow.webContents.openDevTools();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

// 创建中文菜单
function createMenu() {
  const template = [
    {
      label: '文件',
      submenu: [
        {
          label: '退出',
          accelerator: process.platform === 'darwin' ? 'Cmd+Q' : 'Ctrl+Q',
          click: () => {
            app.quit();
          }
        }
      ]
    },
    {
      label: '编辑',
      submenu: [
        { label: '撤销', accelerator: 'CmdOrCtrl+Z', role: 'undo' },
        { label: '重做', accelerator: 'Shift+CmdOrCtrl+Z', role: 'redo' },
        { type: 'separator' },
        { label: '剪切', accelerator: 'CmdOrCtrl+X', role: 'cut' },
        { label: '复制', accelerator: 'CmdOrCtrl+C', role: 'copy' },
        { label: '粘贴', accelerator: 'CmdOrCtrl+V', role: 'paste' },
        { label: '全选', accelerator: 'CmdOrCtrl+A', role: 'selectAll' }
      ]
    },
    {
      label: '视图',
      submenu: [
        { label: '重新加载', accelerator: 'CmdOrCtrl+R', role: 'reload' },
        { label: '强制重新加载', accelerator: 'CmdOrCtrl+Shift+R', role: 'forceReload' },
        { label: '切换开发者工具', accelerator: 'F12', role: 'toggleDevTools' },
        { type: 'separator' },
        { label: '实际大小', accelerator: 'CmdOrCtrl+0', role: 'resetZoom' },
        { label: '放大', accelerator: 'CmdOrCtrl+Plus', role: 'zoomIn' },
        { label: '缩小', accelerator: 'CmdOrCtrl+-', role: 'zoomOut' },
        { type: 'separator' },
        { label: '切换全屏', accelerator: 'F11', role: 'togglefullscreen' }
      ]
    },
    {
      label: '窗口',
      submenu: [
        { label: '最小化', accelerator: 'CmdOrCtrl+M', role: 'minimize' },
        { label: '关闭', accelerator: 'CmdOrCtrl+W', role: 'close' }
      ]
    },
    {
      label: '帮助',
      submenu: [
        {
          label: '关于',
          click: () => {
            // 可以打开关于对话框
          }
        }
      ]
    }
  ];

  // macOS特殊处理
  if (process.platform === 'darwin') {
    template.unshift({
      label: app.getName(),
      submenu: [
        { label: '关于 ' + app.getName(), role: 'about' },
        { type: 'separator' },
        { label: '服务', role: 'services', submenu: [] },
        { type: 'separator' },
        { label: '隐藏 ' + app.getName(), accelerator: 'Command+H', role: 'hide' },
        { label: '隐藏其他', accelerator: 'Command+Shift+H', role: 'hideOthers' },
        { label: '显示全部', role: 'unhide' },
        { type: 'separator' },
        { label: '退出', accelerator: 'Command+Q', click: () => app.quit() }
      ]
    });
  }

  const menu = Menu.buildFromTemplate(template);
  Menu.setApplicationMenu(menu);
}

// 设置自定义缓存目录，避免权限问题导致的缓存错误
// 注意：必须在 app.whenReady() 之前调用
if (isDevelopment()) {
  // 开发环境：使用项目目录下的缓存文件夹
  const userDataPath = path.join(__dirname, '.electron-data');
  const cachePath = path.join(__dirname, '.electron-cache');
  
  // 确保目录存在
  try {
    if (!fs.existsSync(userDataPath)) {
      fs.mkdirSync(userDataPath, { recursive: true });
    }
    if (!fs.existsSync(cachePath)) {
      fs.mkdirSync(cachePath, { recursive: true });
    }
  } catch (error) {
    console.warn('创建缓存目录失败，使用默认路径:', error.message);
  }
  
  // 设置路径（如果目录创建成功）
  try {
    app.setPath('userData', userDataPath);
    app.setPath('cache', cachePath);
  } catch (error) {
    console.warn('设置缓存路径失败，使用默认路径:', error.message);
  }
}

app.whenReady().then(() => {
  createMenu();
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});


// IPC通信处理
ipcMain.handle('get-backend-url', () => {
  const isDev = isDevelopment();
  const config = loadConfig();
  
  if (isDev) {
    // 开发环境：优先使用环境变量，其次使用配置文件，最后使用默认值
    const devUrl = process.env.BACKEND_URL || config.backend.dev || 'http://localhost:8080';
    console.log('[环境] 开发模式，后端地址:', devUrl);
    return devUrl;
  } else {
    // 生产环境：优先使用环境变量，其次使用配置文件，最后使用默认值
    const prodUrl = process.env.BACKEND_URL || config.backend.prod || 'http://188.239.21.115:8080';
    console.log('[环境] 生产模式，后端地址:', prodUrl);
    return prodUrl;
  }
});

// 暴露环境信息给渲染进程
ipcMain.handle('get-env', () => {
  return {
    isDev: isDevelopment(),
    isPackaged: app.isPackaged,
    platform: process.platform
  };
});

