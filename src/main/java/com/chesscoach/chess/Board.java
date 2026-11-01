package com.chesscoach.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Неизменяемая шахматная позиция и полные правила игры: генерация ходов, рокировка,
 * взятие на проходе, превращение, FEN и SAN.
 *
 * Фигуры хранятся в массиве из 64 клеток: положительные числа — белые, отрицательные — чёрные.
 */
public final class Board {

    public static final int EMPTY = 0, PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;

    public static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final int WK = 1, WQ = 2, BK = 4, BQ = 8;

    private static final int[][] KNIGHT_STEPS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING_STEPS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] BISHOP_DIRS = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
    private static final int[][] ROOK_DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final int[] PROMOTIONS = {QUEEN, KNIGHT, ROOK, BISHOP};

    /** Какие права на рокировку остаются после того, как с клетки ушли или на ней побили фигуру. */
    private static final int[] CASTLING_MASK = new int[64];

    static {
        Arrays.fill(CASTLING_MASK, WK | WQ | BK | BQ);
        CASTLING_MASK[0] &= ~WQ;                // a1
        CASTLING_MASK[7] &= ~WK;                // h1
        CASTLING_MASK[4] &= ~(WK | WQ);         // e1
        CASTLING_MASK[56] &= ~BQ;               // a8
        CASTLING_MASK[63] &= ~BK;               // h8
        CASTLING_MASK[60] &= ~(BK | BQ);        // e8
    }

    private final int[] squares;
    private final boolean whiteToMove;
    private final int castling;
    private final int enPassant;
    private final int halfmoveClock;
    private final int fullmoveNumber;
    private final int whiteKing;
    private final int blackKing;

    private Board(int[] squares, boolean whiteToMove, int castling, int enPassant, int halfmoveClock, int fullmoveNumber) {
        this.squares = squares;
        this.whiteToMove = whiteToMove;
        this.castling = castling;
        this.enPassant = enPassant;
        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;
        int wk = -1, bk = -1;
        for (int i = 0; i < 64; i++) {
            if (squares[i] == KING) wk = i;
            else if (squares[i] == -KING) bk = i;
        }
        this.whiteKing = wk;
        this.blackKing = bk;
    }

    public static Board start() {
        return fromFen(START_FEN);
    }

    public static Board fromFen(String fen) {
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Некорректный FEN: " + fen);
        }
        int[] squares = new int[64];
        String[] ranks = parts[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("В FEN должно быть 8 горизонталей: " + fen);
        }
        int whiteKings = 0, blackKings = 0;
        for (int r = 0; r < 8; r++) {
            int file = 0;
            for (char c : ranks[r].toCharArray()) {
                if (Character.isDigit(c)) {
                    file += c - '0';
                } else {
                    if (file > 7) throw new IllegalArgumentException("Некорректный FEN: " + fen);
                    int piece = pieceFromChar(c);
                    if (piece == KING) whiteKings++;
                    if (piece == -KING) blackKings++;
                    squares[(7 - r) * 8 + file++] = piece;
                }
            }
            if (file != 8) throw new IllegalArgumentException("Некорректный FEN: " + fen);
        }
        if (whiteKings != 1 || blackKings != 1) {
            throw new IllegalArgumentException("У каждой стороны должен быть один король: " + fen);
        }
        int castling = 0;
        if (parts[2].indexOf('K') >= 0) castling |= WK;
        if (parts[2].indexOf('Q') >= 0) castling |= WQ;
        if (parts[2].indexOf('k') >= 0) castling |= BK;
        if (parts[2].indexOf('q') >= 0) castling |= BQ;
        int ep = "-".equals(parts[3]) ? -1 : squareFromName(parts[3]);
        int halfmove = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        int fullmove = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
        return new Board(squares, "w".equals(parts[1]), castling, ep, halfmove, fullmove);
    }

    public String toFen() {
        return positionKey() + " " + halfmoveClock + " " + fullmoveNumber;
    }

    /** FEN без счётчиков ходов — ключ для определения троекратного повторения. */
    public String positionKey() {
        StringBuilder sb = new StringBuilder();
        for (int r = 7; r >= 0; r--) {
            int empty = 0;
            for (int f = 0; f < 8; f++) {
                int p = squares[r * 8 + f];
                if (p == EMPTY) {
                    empty++;
                } else {
                    if (empty > 0) sb.append(empty);
                    empty = 0;
                    sb.append(pieceChar(p));
                }
            }
            if (empty > 0) sb.append(empty);
            if (r > 0) sb.append('/');
        }
        sb.append(whiteToMove ? " w " : " b ");
        if (castling == 0) {
            sb.append('-');
        } else {
            if ((castling & WK) != 0) sb.append('K');
            if ((castling & WQ) != 0) sb.append('Q');
            if ((castling & BK) != 0) sb.append('k');
            if ((castling & BQ) != 0) sb.append('q');
        }
        sb.append(' ').append(enPassant < 0 ? "-" : squareName(enPassant));
        return sb.toString();
    }

    public boolean isWhiteToMove() {
        return whiteToMove;
    }

    public int pieceAt(int square) {
        return squares[square];
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public boolean inCheck() {
        return isAttacked(whiteToMove ? whiteKing : blackKing, !whiteToMove);
    }

    /** Все легальные ходы стороны, которая сейчас ходит. */
    public List<Move> legalMoves() {
        List<Move> pseudo = pseudoLegalMoves();
        List<Move> legal = new ArrayList<>(pseudo.size());
        for (Move m : pseudo) {
            if (!apply(m).leavesMoverInCheck()) {
                legal.add(m);
            }
        }
        return legal;
    }

    /** Ходы, которые могут оставить своего короля под шахом. Движку быстрее проверять их по одному. */
    public List<Move> pseudoLegalMoves() {
        List<Move> moves = new ArrayList<>(48);
        int sign = whiteToMove ? 1 : -1;
        for (int from = 0; from < 64; from++) {
            int piece = squares[from] * sign;
            if (piece <= 0) continue;
            switch (piece) {
                case PAWN -> addPawnMoves(from, sign, moves);
                case KNIGHT -> addSteps(from, KNIGHT_STEPS, sign, moves);
                case BISHOP -> addSlides(from, BISHOP_DIRS, sign, moves);
                case ROOK -> addSlides(from, ROOK_DIRS, sign, moves);
                case QUEEN -> {
                    addSlides(from, BISHOP_DIRS, sign, moves);
                    addSlides(from, ROOK_DIRS, sign, moves);
                }
                default -> {
                    addSteps(from, KING_STEPS, sign, moves);
                    addCastling(moves);
                }
            }
        }
        return moves;
    }

    /** После хода: остался ли под шахом король той стороны, которая только что сходила. */
    public boolean leavesMoverInCheck() {
        return isAttacked(whiteToMove ? blackKing : whiteKing, whiteToMove);
    }

    private void addPawnMoves(int from, int sign, List<Move> moves) {
        int forward = from + 8 * sign;
        int file = from % 8;
        int rank = from / 8;
        boolean promotes = whiteToMove ? rank == 6 : rank == 1;
        if (squares[forward] == EMPTY) {
            addPawnMove(from, forward, promotes, moves);
            boolean onStartRank = whiteToMove ? rank == 1 : rank == 6;
            if (onStartRank && squares[forward + 8 * sign] == EMPTY) {
                moves.add(Move.of(from, forward + 8 * sign));
            }
        }
        for (int df = -1; df <= 1; df += 2) {
            int f = file + df;
            if (f < 0 || f > 7) continue;
            int target = forward + df;
            if (squares[target] * sign < 0) {
                addPawnMove(from, target, promotes, moves);
            } else if (target == enPassant) {
                moves.add(Move.of(from, target));
            }
        }
    }

    private void addPawnMove(int from, int to, boolean promotes, List<Move> moves) {
        if (promotes) {
            for (int p : PROMOTIONS) moves.add(new Move(from, to, p));
        } else {
            moves.add(Move.of(from, to));
        }
    }

    private void addSteps(int from, int[][] steps, int sign, List<Move> moves) {
        int file = from % 8, rank = from / 8;
        for (int[] s : steps) {
            int f = file + s[0], r = rank + s[1];
            if (f < 0 || f > 7 || r < 0 || r > 7) continue;
            int to = r * 8 + f;
            if (squares[to] * sign <= 0) moves.add(Move.of(from, to));
        }
    }

    private void addSlides(int from, int[][] dirs, int sign, List<Move> moves) {
        int file = from % 8, rank = from / 8;
        for (int[] d : dirs) {
            int f = file + d[0], r = rank + d[1];
            while (f >= 0 && f <= 7 && r >= 0 && r <= 7) {
                int to = r * 8 + f;
                int target = squares[to] * sign;
                if (target > 0) break;
                moves.add(Move.of(from, to));
                if (target < 0) break;
                f += d[0];
                r += d[1];
            }
        }
    }

    private void addCastling(List<Move> moves) {
        if (whiteToMove) {
            addCastle(WK, 4, 7, new int[]{5, 6}, new int[]{4, 5, 6}, 6, ROOK, moves);
            addCastle(WQ, 4, 0, new int[]{1, 2, 3}, new int[]{4, 3, 2}, 2, ROOK, moves);
        } else {
            addCastle(BK, 60, 63, new int[]{61, 62}, new int[]{60, 61, 62}, 62, -ROOK, moves);
            addCastle(BQ, 60, 56, new int[]{57, 58, 59}, new int[]{60, 59, 58}, 58, -ROOK, moves);
        }
    }

    private void addCastle(int right, int king, int rook, int[] mustBeEmpty, int[] mustBeSafe, int kingTo, int rookPiece, List<Move> moves) {
        if ((castling & right) == 0 || squares[rook] != rookPiece) return;
        if (squares[king] != (whiteToMove ? KING : -KING)) return;
        for (int s : mustBeEmpty) {
            if (squares[s] != EMPTY) return;
        }
        for (int s : mustBeSafe) {
            if (isAttacked(s, !whiteToMove)) return;
        }
        moves.add(Move.of(king, kingTo));
    }

    /** Атакована ли клетка фигурами указанного цвета. */
    public boolean isAttacked(int square, boolean byWhite) {
        int sign = byWhite ? 1 : -1;
        int file = square % 8, rank = square / 8;

        // Пешка атакует по диагонали вперёд, значит искать её нужно на шаг «назад» от клетки.
        int pawnRank = rank - sign;
        if (pawnRank >= 0 && pawnRank <= 7) {
            if (file > 0 && squares[pawnRank * 8 + file - 1] == PAWN * sign) return true;
            if (file < 7 && squares[pawnRank * 8 + file + 1] == PAWN * sign) return true;
        }
        if (attackedByStep(file, rank, KNIGHT_STEPS, KNIGHT * sign)) return true;
        if (attackedByStep(file, rank, KING_STEPS, KING * sign)) return true;
        return attackedBySlide(file, rank, BISHOP_DIRS, BISHOP * sign, QUEEN * sign)
                || attackedBySlide(file, rank, ROOK_DIRS, ROOK * sign, QUEEN * sign);
    }

    private boolean attackedByStep(int file, int rank, int[][] steps, int piece) {
        for (int[] s : steps) {
            int f = file + s[0], r = rank + s[1];
            if (f >= 0 && f <= 7 && r >= 0 && r <= 7 && squares[r * 8 + f] == piece) return true;
        }
        return false;
    }

    private boolean attackedBySlide(int file, int rank, int[][] dirs, int slider, int queen) {
        for (int[] d : dirs) {
            int f = file + d[0], r = rank + d[1];
            while (f >= 0 && f <= 7 && r >= 0 && r <= 7) {
                int p = squares[r * 8 + f];
                if (p != EMPTY) {
                    if (p == slider || p == queen) return true;
                    break;
                }
                f += d[0];
                r += d[1];
            }
        }
        return false;
    }

    /** Возвращает новую позицию после хода. Легальность хода проверяет вызывающий код. */
    public Board apply(Move m) {
        int[] next = squares.clone();
        int piece = next[m.from()];
        int abs = Math.abs(piece);
        boolean capture = next[m.to()] != EMPTY;

        next[m.from()] = EMPTY;
        next[m.to()] = m.promotion() != EMPTY ? m.promotion() * Integer.signum(piece) : piece;

        if (abs == PAWN && m.to() == enPassant) {
            next[m.to() + (whiteToMove ? -8 : 8)] = EMPTY;
            capture = true;
        }
        if (abs == KING && Math.abs(m.to() - m.from()) == 2) {
            int rookFrom = m.to() > m.from() ? m.to() + 1 : m.to() - 2;
            int rookTo = m.to() > m.from() ? m.to() - 1 : m.to() + 1;
            next[rookTo] = next[rookFrom];
            next[rookFrom] = EMPTY;
        }
        int newEp = -1;
        if (abs == PAWN && Math.abs(m.to() - m.from()) == 16) {
            newEp = (m.from() + m.to()) / 2;
        }
        int newCastling = castling & CASTLING_MASK[m.from()] & CASTLING_MASK[m.to()];
        int clock = (abs == PAWN || capture) ? 0 : halfmoveClock + 1;
        int fullmove = whiteToMove ? fullmoveNumber : fullmoveNumber + 1;
        return new Board(next, !whiteToMove, newCastling, newEp, clock, fullmove);
    }

    /** Ни одна сторона не может поставить мат: K против K, K+лёгкая против K. */
    public boolean isInsufficientMaterial() {
        int minors = 0;
        for (int p : squares) {
            switch (Math.abs(p)) {
                case PAWN, ROOK, QUEEN -> {
                    return false;
                }
                case KNIGHT, BISHOP -> minors++;
                default -> {
                }
            }
        }
        return minors <= 1;
    }

    /** Запись хода в стандартной алгебраической нотации (SAN). Ход должен быть легальным. */
    public String toSan(Move m) {
        int abs = Math.abs(squares[m.from()]);
        StringBuilder san = new StringBuilder();
        if (abs == KING && Math.abs(m.to() - m.from()) == 2) {
            san.append(m.to() > m.from() ? "O-O" : "O-O-O");
        } else {
            boolean capture = squares[m.to()] != EMPTY || (abs == PAWN && m.to() == enPassant);
            if (abs == PAWN) {
                if (capture) san.append(squareName(m.from()).charAt(0));
            } else {
                san.append(Character.toUpperCase(pieceChar(abs)));
                san.append(disambiguation(m, abs));
            }
            if (capture) san.append('x');
            san.append(squareName(m.to()));
            if (m.promotion() != EMPTY) {
                san.append('=').append(Character.toUpperCase(pieceChar(m.promotion())));
            }
        }
        Board after = apply(m);
        if (after.inCheck()) {
            san.append(after.legalMoves().isEmpty() ? '#' : '+');
        }
        return san.toString();
    }

    private String disambiguation(Move m, int abs) {
        boolean ambiguous = false, sameFile = false, sameRank = false;
        for (Move other : legalMoves()) {
            if (other.to() == m.to() && other.from() != m.from() && Math.abs(squares[other.from()]) == abs) {
                ambiguous = true;
                if (other.from() % 8 == m.from() % 8) sameFile = true;
                if (other.from() / 8 == m.from() / 8) sameRank = true;
            }
        }
        if (!ambiguous) return "";
        String name = squareName(m.from());
        if (!sameFile) return name.substring(0, 1);
        if (!sameRank) return name.substring(1);
        return name;
    }

    public static String squareName(int square) {
        return "" + (char) ('a' + square % 8) + (char) ('1' + square / 8);
    }

    public static int squareFromName(String name) {
        if (name.length() != 2 || name.charAt(0) < 'a' || name.charAt(0) > 'h' || name.charAt(1) < '1' || name.charAt(1) > '8') {
            throw new IllegalArgumentException("Некорректная клетка: " + name);
        }
        return (name.charAt(1) - '1') * 8 + (name.charAt(0) - 'a');
    }

    private static int pieceFromChar(char c) {
        int abs = switch (Character.toLowerCase(c)) {
            case 'p' -> PAWN;
            case 'n' -> KNIGHT;
            case 'b' -> BISHOP;
            case 'r' -> ROOK;
            case 'q' -> QUEEN;
            case 'k' -> KING;
            default -> throw new IllegalArgumentException("Неизвестная фигура: " + c);
        };
        return Character.isUpperCase(c) ? abs : -abs;
    }

    private static char pieceChar(int piece) {
        char c = "?pnbrqk".charAt(Math.abs(piece));
        return piece > 0 ? Character.toUpperCase(c) : c;
    }
}
