package touhouFinal;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

import touhouFinal.TouhouFinal.Difficulty;

public class TouhouFinal extends JPanel implements ActionListener {
    // --- ゲーム設定 ---
    private final int WIDTH = 600, HEIGHT = 600;
    private final int GAME_WIDTH = 400; // ゲーム領域
    private enum State { TITLE, PLAYING, GAMEOVER }
    private State currentState = State.TITLE;
    enum Difficulty {
        EASY(1.5, 0.5, 8),    // 弾速倍率, 発射頻度倍率, 全方位弾の数
        NORMAL(2.5, 1.0, 16),
        HARD(4.0, 1.5, 32);

        final double bulletSpeed;
        final double fireRate;
        final int wayCount;

        Difficulty(double s, double r, int w) {
            this.bulletSpeed = s;
            this.fireRate = r;
            this.wayCount = w;
        }
    }

    // ゲーム全体で使う現在の難易度
    Difficulty currentDiff = Difficulty.NORMAL;

    // --- ゲームオブジェクト ---
    private Player player = new Player();
    private ArrayList<Enemy> enemies = new ArrayList<>();
    private ArrayList<Bullet> pBullets = new ArrayList<>();
    private ArrayList<Bullet> eBullets = new ArrayList<>();
    private ArrayList<Item> items = new ArrayList<>();

    // --- システム変数 ---
    private int gameTimer = 0;
    private long score = 0;
    private int graze = 0;
    private double bgY = 0;
    private boolean[] keys = new boolean[256];

    public TouhouFinal() {
        Timer timer = new Timer(16, this);
        timer.start();
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) { if(e.getKeyCode()<256) keys[e.getKeyCode()] = true; }
            public void keyReleased(KeyEvent e) { if(e.getKeyCode()<256) keys[e.getKeyCode()] = false; }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (currentState == State.TITLE) {
        	// keyPressed内
        	    if (keys[KeyEvent.VK_UP]) currentDiff = Difficulty.EASY;
        	    if (keys[KeyEvent.VK_RIGHT]) currentDiff = Difficulty.NORMAL;
        	    if (keys[KeyEvent.VK_DOWN]) currentDiff = Difficulty.HARD;
            if (keys[KeyEvent.VK_Z]) {
                resetGame();
                currentState = State.PLAYING;
            }
        } else if (currentState == State.PLAYING) {
            updateGame();
        } else if (currentState == State.GAMEOVER) {
            if (keys[KeyEvent.VK_Z]) currentState = State.TITLE;
        }
        repaint();
    }

    private void resetGame() {
        player = new Player();
        enemies.clear(); eBullets.clear(); pBullets.clear(); items.clear();
        gameTimer = 0; score = 0; graze = 0;
    }

    private void updateGame() {
        gameTimer++;
        bgY = (bgY + 1) % HEIGHT;

        // 自機移動 & 射撃
        player.update(keys);
        if (keys[KeyEvent.VK_Z] && gameTimer % 5 == 0) {
            pBullets.add(new Bullet(player.x, player.y, -Math.PI/2, 10, false));
        }
        if (keys[KeyEvent.VK_X] && player.bombs > 0 && player.bombTimer == 0) {
            player.triggerBomb(eBullets);
        }

        // 敵出現パターン
        if (gameTimer % 100 == 0) enemies.add(new Enemy(Math.random()*300+50, -20));

        // 各種更新
        updateList(enemies);
        updateList(pBullets);
        updateList(eBullets);
        updateList(items);

        // 当たり判定
        checkCollisions();
    }

    private void checkCollisions() {
        // 敵弾 vs 自機
        for (Bullet b : eBullets) {
            double dist = Math.hypot(b.x - player.x, b.y - player.y);
            if (player.invTimer == 0 && player.bombTimer == 0) {
                if (dist < 3) { currentState = State.GAMEOVER; } // 当たり
                else if (dist < 15 && !b.grazed) { b.grazed = true; graze++; score += 500; } // グレイズ
            }
        }
        // 自機弾 vs 敵
        for (Bullet pb : pBullets) {
            for (Enemy en : enemies) {
                if (Math.hypot(pb.x - en.x, pb.y - en.y) < 20) {
                    en.hp--; pb.active = false;
                    if (en.hp <= 0) { 
                        en.active = false; score += 10000; 
                        items.add(new Item(en.x, en.y));
                    }
                }
            }
        }
        // アイテム回収
        for (Item it : items) {
            if (player.y < 150 || Math.hypot(it.x - player.x, it.y - player.y) < 50) it.attracted = true;
            if (Math.hypot(it.x - player.x, it.y - player.y) < 15) { it.active = false; score += 5000; }
        }
    }

    private void updateList(ArrayList<? extends GameObject> list) {
        Iterator<? extends GameObject> it = list.iterator();
        while (it.hasNext()) {
            GameObject obj = it.next();
            obj.update();
            
            if (obj instanceof Enemy) ((Enemy)obj).attack(eBullets, player, currentDiff);
            if (obj instanceof Item) ((Item)obj).attract(player.x, player.y);
            
            // 判定：アクティブでない(HP0になった)、または画面外に出た場合に削除
            if (!obj.active || obj.x < -50 || obj.x > 450 || obj.y < -50 || obj.y > 650) {
                it.remove();
            }
        }
    }
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 背景
        g2.setColor(new Color(20, 20, 40));
        g2.fillRect(0, 0, GAME_WIDTH, HEIGHT);
        g2.setColor(new Color(40, 40, 80));
        for(int i=-1; i<2; i++) g2.drawLine(0, (int)bgY+i*200, GAME_WIDTH, (int)bgY+i*200);

        if (currentState == State.TITLE) {
            g2.setColor(Color.WHITE);
            g2.drawString("TOUHOU JAVA ENGINE", 120, 200);
            
            // 現在の難易度を表示
            g2.drawString("SELECT DIFFICULTY (UP/RIGHT/DOWN)", 100, 280);
            g2.setFont(new Font("Monospaced", Font.BOLD, 24));
            
            // 選択中の難易度を色付け
            g2.setColor(currentDiff == Difficulty.EASY ? Color.GREEN : Color.GRAY);
            g2.drawString(" EASY ", 150, 320);
            
            g2.setColor(currentDiff == Difficulty.NORMAL ? Color.YELLOW : Color.GRAY);
            g2.drawString(" NORMAL ", 150, 350);
            
            g2.setColor(currentDiff == Difficulty.HARD ? Color.RED : Color.GRAY);
            g2.drawString(" HARD ", 150, 380);
            
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2.setColor(Color.WHITE);
            g2.drawString("PRESS Z TO START", 140, 450);
        } else {
            for (Bullet b : pBullets) { g2.setColor(Color.CYAN); g2.fillOval((int)b.x-3, (int)b.y-3, 6, 6); }
            for (Bullet b : eBullets) { g2.setColor(Color.WHITE); g2.fillOval((int)b.x-3, (int)b.y-3, 6, 6); }
            for (Enemy en : enemies) { g2.setColor(Color.MAGENTA); g2.fillRect((int)en.x-15, (int)en.y-15, 30, 30); }
            for (Item it : items) { g2.setColor(Color.YELLOW); g2.fillRect((int)it.x-5, (int)it.y-5, 10, 10); }
            
            // 自機描画
            if (player.invTimer % 4 < 2) {
                g2.setColor(Color.WHITE);
                g2.fillOval((int)player.x-10, (int)player.y-10, 20, 20);
                if (keys[KeyEvent.VK_SHIFT]) { g2.setColor(Color.RED); g2.fillOval((int)player.x-3, (int)player.y-3, 6, 6); }
            }
            
            // ボム演出
            if (player.bombTimer > 0) {
                g2.setColor(new Color(255, 255, 255, 100));
                int r = (120 - player.bombTimer) * 8;
                g2.drawOval((int)player.x - r, (int)player.y - r, r*2, r*2);
            }

            // サイドバー
            g2.setClip(GAME_WIDTH, 0, 200, HEIGHT);
            g2.setColor(Color.BLACK); g2.fillRect(GAME_WIDTH, 0, 200, HEIGHT);
            g2.setColor(Color.WHITE);
            g2.drawString(String.format("SCORE: %,d", score), 420, 50);
            g2.drawString("GRAZE: " + graze, 420, 80);
            g2.drawString("BOMB:  " + player.bombs, 420, 110);
            if (currentState == State.GAMEOVER) g2.drawString("GAME OVER - Z to TITLE", 420, 300);
        }
    }

    public static void main(String[] args) {
        JFrame f = new JFrame();
        f.add(new TouhouFinal());
        f.setSize(600, 600);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
    }
}

// --- サブクラス群 ---
abstract class GameObject { double x, y; boolean active = true; abstract void update(); }

class Player {
    double x = 200, y = 500; int bombs = 3, bombTimer = 0, invTimer = 0;
    void update(boolean[] keys) {
        double s = keys[16] ? 2 : 4; // Shift
        if (keys[KeyEvent.VK_UP]) y -= s; if (keys[KeyEvent.VK_DOWN]) y += s;
        if (keys[KeyEvent.VK_LEFT]) x -= s; if (keys[KeyEvent.VK_RIGHT]) x += s;
        if (bombTimer > 0) bombTimer--; if (invTimer > 0) invTimer--;
    }
    void triggerBomb(ArrayList<Bullet> eb) { bombs--; bombTimer = 120; invTimer = 180; eb.clear(); }
}

class Enemy extends GameObject {
    int hp = 10, timer = 0;
    Enemy(double x, double y) { this.x = x; this.y = y; }
    void update() { if (y < 100) y += 2; x += Math.sin(Math.toRadians(timer++)) * 2; }
    void attack(ArrayList<Bullet> eb, Player p, Difficulty diff) {
        // 1. 発射頻度の調整（NORMALの1.5倍速く撃つなど）
        if (timer % (int)(40 / diff.fireRate) == 0) {
            double angle = Math.atan2(p.y - y, p.x - x);
            // 弾速を難易度に応じて変える
            eb.add(new Bullet(x, y, angle, diff.bulletSpeed, true));
        }

        // 2. 全方位弾の「密度」を変える
        if (timer % 100 == 0) {
            int count = diff.wayCount; // Easyなら8発、Hardなら32発
            for(int i = 0; i < count; i++) {
                eb.add(new Bullet(x, y, Math.toRadians(360.0/count * i), diff.bulletSpeed * 0.8, true));
            }
        }
    }
}

class Bullet extends GameObject {
    double vx, vy; boolean grazed = false;
    Bullet(double x, double y, double a, double s, boolean enemy) {
        this.x = x; this.y = y; vx = Math.cos(a) * s; vy = Math.sin(a) * s;
    }
    void update() { x += vx; y += vy; }
}

class Item extends GameObject {
    boolean attracted = false;
    Item(double x, double y) { this.x = x; this.y = y; }
    void update() { if (!attracted) y += 2; }
    void attract(double px, double py) {
        if (attracted) {
            double a = Math.atan2(py - y, px - x);
            x += Math.cos(a) * 8; y += Math.sin(a) * 8;
        }
    }
}
