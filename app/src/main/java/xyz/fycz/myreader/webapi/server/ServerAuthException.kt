/*
 * This file is part of FYReader.
 * FYReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FYReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with FYReader.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2020 - 2022 fengyuecanzhu
 */

package xyz.fycz.myreader.webapi.server

import java.io.IOException

/**
 * 服务端认证失效（HTTP 401 / 业务码 401）。
 *
 * 继承 [IOException]，因此所有按 [IOException] 捕获的既有调用点无需改动即可继续工作；
 * 需要区分「登录失效」时用 `catch (e: ServerAuthException)` 或 `is ServerAuthException` 判断。
 *
 * 抛出前 [ServerApi] 已触发全局认证失效回调，UI 侧会被引导到「本机书库」设置页重新登录，
 * 不会出现界面只弹一个裸 IOException 文案、用户不知道要做什么的情况。
 *
 * @author fengyue
 */
class ServerAuthException(message: String) : IOException(message)
