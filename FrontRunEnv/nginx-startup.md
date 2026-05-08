# Sky-Takeout Nginx 启动说明

## 1. 环境说明

- 当前 Nginx 目录：`D:\ProgramFiles\CodeProjects\Sky-Takeout\FrontRunEnv\nginx-1.20.2`
- 当前前端访问端口：`8081`
- 当前后端代理目标：`http://localhost:8080`

## 2. 启动前准备

1. 确认后端服务已经启动，并监听 `8080` 端口。
2. 确认 Nginx 目录不要放在包含中文或乱码的路径下。
3. 确认 `8081` 端口未被其他程序占用。

## 3. 启动命令

在 PowerShell 中进入 Nginx 目录后执行：

```powershell
cd D:\ProgramFiles\CodeProjects\Sky-Takeout\FrontRunEnv\nginx-1.20.2
.\nginx.exe
```

如果要先检查配置是否正确，可以执行：

```powershell
cd D:\ProgramFiles\CodeProjects\Sky-Takeout\FrontRunEnv\nginx-1.20.2
.\nginx.exe -t
```

## 4. 访问地址

- 前端首页：`http://localhost:8081/`
- 管理端接口代理：`http://localhost:8081/api/`
- 用户端接口代理：`http://localhost:8081/user/`
- WebSocket 代理：`http://localhost:8081/ws/`

## 5. 停止与重载

停止 Nginx：

```powershell
cd D:\ProgramFiles\CodeProjects\Sky-Takeout\FrontRunEnv\nginx-1.20.2
.\nginx.exe -s stop
```

修改配置后重新加载：

```powershell
cd D:\ProgramFiles\CodeProjects\Sky-Takeout\FrontRunEnv\nginx-1.20.2
.\nginx.exe -s reload
```

## 6. 常见问题

### 6.1 启动时报找不到 `mime.types`

说明 `conf` 目录缺少 `mime.types` 文件，当前项目已补齐。

### 6.2 启动时报端口被占用

可以用下面命令查看端口占用：

```powershell
Get-NetTCPConnection -State Listen | Where-Object {$_.LocalPort -eq 8081}
```

如果 `8081` 被占用，可以修改 `conf\nginx.conf` 中的 `listen` 端口后重启。

### 6.3 页面能打开但接口报错

通常说明后端 `8080` 没有启动，或者后端服务启动失败。先确认后端服务是否正常运行，再访问前端页面。
