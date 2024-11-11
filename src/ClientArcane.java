import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

/**
 * ArcaneRealm v1.5: Client
 * by PaperFish, 2024.11.6
 */
public class ClientArcane {
    private Socket clientSocket;
    private BufferedReader listener;
    private PrintWriter speaker;
    private final NightShell shell;
    private String guest = "";
    private String host = "";
    private int port = -1;
    private String name = "";
    private Thread Ear;
    private boolean DoEar = false;

    public ClientArcane() {
        shell = new NightShell("Client") {
            @Override protected void processWindowEvent(WindowEvent e) {
                if (e.getID() == WindowEvent.WINDOW_CLOSING) {
                    if (DoEar) {
                        if (JOptionPane.showConfirmDialog(this, "确定要退出讨论间吗？", "EXIT", JOptionPane.OK_CANCEL_OPTION) != 0)
                            return;
                        say(ExitSys);}
                } super.processWindowEvent(e);
            }
            @Override void EnterInput() {super.EnterInput(); if (setHost() && setPort() && setName()) say(input);}
            @Override boolean setHost() {
                if (guest.isBlank()) return false;
                if (!host.isBlank()) return true;
                if (!input.isBlank()) {host = input; println(new1(input, LIGHT_GREY), true);
                    print(newWhisper("端口号："), true); return false;}
                println(new1("无效的地址", SOFT_RED), true);
                print(newWhisper("服务器地址（IPv4）："), true); return false;
            }
            @Override boolean setPort() {
                if (port != -1) return true; int P;
                if (input.isBlank() || !input.matches("[0-9]+") || ((P = Integer.parseInt(input)) < 0) || (P > 65535)) {
                    println(new1("无效的端口号", SOFT_RED), true);
                } else {
                    print(new1(input, LIGHT_GREY), true);
                    try {
                        clientSocket = new Socket();
                        clientSocket.connect(new InetSocketAddress(host, P), 2500); port = P;
                        shell.print(" -> %o\n你的名称：", "可用", Color.GREEN, true);
                        Notify(); return false;
                    } catch (IOException ex) {
                        shell.print(" -> %o：服务器未开启或无法连接\n", "连接超时", SOFT_RED, true);
                    }
                }
                print(newWhisper("端口号："), true); return false;
            }
            @Override boolean setName() {
                if (!name.isBlank()) return true;
                if (input.isBlank()) println(new1("无效的名称", SOFT_RED), true);
                else if (tryJoin(input)) {name = input; Notify(); return false;}
                print(newWhisper("你的名称："), true); return false;
            }
        };
        try {
            String localIPv4 = NightShell.getLocalIPv4Address();
            if (localIPv4 == null) shell.print("%o至网络", "无法连接", NightShell.HARD_RED, true);
            else {guest = localIPv4; shell.print("服务器地址（IPv4）：", true);}
        } catch (SocketException e) {
            shell.printlnException("尝试获取网络地址时出错：", e);
        }
    }
    private boolean tryJoin(String tryName) {
        shell.print(tryName, NightShell.SOFT_WHITE, true);
        request(NightShell.JoinRequest, tryName);
        boolean accept = false;
        try {
            String[] W = shell.deserialized(listener.readLine()).words;
            switch (W[0]) {
                case NightShell.BanNewClient -> shell.print(" -> 访问被%o，讨论间目前禁止新成员加入\n", "拒绝", NightShell.SOFT_RED, true);
                case NightShell.UnusableName -> shell.print(" -> 名称%o，请更换\n", W[1], NightShell.LIGHT_GREY, true);
                case NightShell.JoinReject -> shell.print("\n连接失败，错误发送的请求：%o\n", W[0], NightShell.LIGHT_GREY, true);
                case NightShell.JoinAccept -> accept = true;
                default -> shell.print("\n连接失败，未知的服务器应答：%o\n", W[0], NightShell.LIGHT_GREY, true);
            }
        } catch (SocketException e) {shell.printlnException("连接超时：", e);
        } catch (IOException e) {e.printStackTrace();}
        return accept;
    }

    private synchronized void Notify() {notify();}
    public synchronized void communicate() {
        while (port == -1) {try {wait();} catch (InterruptedException e) {e.printStackTrace();}}
        try {
            listener = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            speaker = new PrintWriter(clientSocket.getOutputStream(), true);
        } catch (IOException e) {
            shell.printlnException("建立通信时发生错误：", e); return;
        }
        report(NightShell.newOrder(NightShell.GuestIPv4, guest));
        while (name.isBlank()) {try {wait();} catch (InterruptedException e) {e.printStackTrace();}}
        shell.clearWhisper();
        shell.print("已%o到", "连接", NightShell.HARD_GREEN, false);
        shell.print("位于Ipv4:%o ", host, Color.WHITE, false);
        shell.print("上的服务器（端口：%o）\n", port, Color.WHITE, false);
        shell.print("你已进入讨论间\n\n", false);
        EarMonite();
    }

    /**
     * 监听服务器的信息
     */
    public void EarMonite() {
        Ear = new Thread(() -> {
            DoEar = true;
            try {
                String serMess;
                while (DoEar) {
                    if ((serMess = listener.readLine()) == null || serMess.isBlank()) continue;
                    NightShell.Message message = shell.deserialized(serMess);
                    switch (message.type) {
                        case ':' -> shell.println(message, false);
                        case '/' -> execute(message);
                    }
                }
                listener.close();
                speaker.close();
                clientSocket.close();
            } catch (IOException e) {
                shell.printlnException("连接已丢失：", e);
                e.printStackTrace();
            }
        });
        Ear.start();
    }

    public void EarClose() {Ear.interrupt(); DoEar = false; shell.resetTitle("Client");}

    /**
     * 控制台输入：'/'开头为指令，否则作为内容向服务器报告
     */
    public void say(String words) {
        if (words.isEmpty()) return;
        try {
            if (words.charAt(0) == '/') {command(words);}
            else request(NightShell.TALK, words);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 向服务器报告
     */
    private void report(NightShell.Message message) {speaker.println(message.serialized());}

    /**
     * 执行客户端指令
     */
    private void command(String M) throws IOException {
        String[] cmd = M.split(" ");
        switch (cmd[0]) {
            case NightShell.Help -> shell.print(HELP_TEXT, true);
            case NightShell.ColorHint -> {
                shell.print("以%o为主的信息：所有人都可见的聊天内容\n", "白色", NightShell.SOFT_WHITE, true);
                shell.print("以%o为主的信息：所有人都可见的系统告示\n", "浅灰", NightShell.LIGHT_GREY, true);
                shell.print("以%o为主的信息：仅你自己可见的系统提示\n", "深灰", NightShell.SOFT_GREY, true);
                shell.print("成员有两个特征色彩：\nMainColor[%o]在主格上使用\n", "成员名", NightShell.LIGHT_AQUA, true);
                shell.print("MinorColor[%o]在宾格上使用\n", "成员名", NightShell.DARK_AQUA, true);
            }
            case NightShell.ClearWhisper -> shell.clearWhisper();
            case NightShell.MemberList, NightShell.HostPort -> request(M);
            case NightShell.ExitSys -> {
                request(M); shell.print("你已离开讨论间\n", NightShell.LIGHT_GREY, false);
                EarClose(); shell.print("已断开与服务器的连接\n", false);
            }
            case NightShell.ReColor -> {
                if (cmd.length == 3) {
                    if (NightShell.hexColor(cmd[1]) != null && NightShell.hexColor(cmd[2]) != null) {
                        request(NightShell.ReColor, cmd[1], cmd[2]);
                    } else shell.print("错误的格式，示例：/rec 41CCFFFF 0C8EBEFF\n", true);
                } else shell.print("用法（十六进制颜色码）：/rec [MainColor] [MinorColor]\n", true);
            }
            default -> shell.print("未知指令，/H 查看指令帮助\n", true);
        }
    }

    /**
     * 向服务器发送请求 -> ServerArcane.response()
     */
    private void request(String order, String... args) {
        switch (order) {
            case NightShell.JoinRequest, NightShell.TALK -> report(NightShell.newOrder(order, args[0]));
            case NightShell.ReColor -> report(NightShell.newOrder(order, args[0]+" "+args[1]));
            default -> report(NightShell.newOrder(order, 0));
        }
    }

    /**
     * 执行服务器指示 <- ServerArcane.order()
     */
    private void execute(NightShell.Message M) throws IOException {
        String K = M.words[0];
        switch (K) {
            case NightShell.UpdateTitle -> updateParaText(M.words[1].split(" "));
            case NightShell.INFO -> shell.println(shell.deserialized(M.words[1]), true);
            case NightShell.TerminalSys, NightShell.ExitSys -> {EarClose(); shell.print("失去与服务器的连接\n", false);}
            case NightShell.AllowTextHighlight -> shell.setDisplayHighlightable(true);
            case NightShell.BanTextHighlight -> shell.setDisplayHighlightable(false);
        }
    }


    public void updateParaText(String[] args) {
        shell.setTitle(String.format("%s | 客户端：%s | (在线人数：%s)", args[0], name, args[1]));
    }

    private static final String HELP_TEXT = """
                /H    指令帮助
                /L    成员列表
                /E    退出房间
                /C    清空提示字
                /rec  自定义特征色
                /color 查看色彩规范
                /host 查看地址与端口号
                """;

    public static void main(String[] args) {new ClientArcane().communicate();}
}