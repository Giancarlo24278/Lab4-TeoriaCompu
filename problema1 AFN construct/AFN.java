import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.swing.*;

/**
 * Laboratorio 4 - Problema 1
 */
public class AFN {

    private static final String EPSILON = "ε";
    private static int nextStateId = 0;

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        String archivo = "expresiones.txt";

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(archivo), StandardCharsets.UTF_8))) {

            Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
            String expresion;
            int numero = 1;

            while ((expresion = br.readLine()) != null) {
                expresion = expresion.trim();
                if (expresion.isEmpty()) continue;

                System.out.println("\n==================================================");
                System.out.println("EXPRESION " + numero);
                System.out.println("REGEX: " + expresion);

                String procesada = expandirOperadores(expresion);
                procesada = insertarConcatenacion(procesada);
                String postfix = convertirPostfix(procesada);

                System.out.println("PROCESADA: " + procesada);
                System.out.println("POSTFIX:   " + postfix);

                Nodo raiz = construirArbolSintactico(postfix);

                System.out.println("\nARBOL SINTACTICO:");
                imprimirArbol(raiz, "", true);

                nextStateId = 0;
                Fragmento fragmento = thompson(raiz);

                System.out.println("\nAFN GENERADO:");
                imprimirAFN(fragmento.inicio);

                mostrarAFNGrafico(fragmento.inicio, fragmento.fin,
                        "AFN Thompson - Expresion " + numero + ": " + expresion);

                System.out.print("\nIngrese la cadena w para simular (ENTER = cadena vacia): ");
                String w = scanner.nextLine();

                boolean aceptada = simularAFN(fragmento.inicio, fragmento.fin, w);

                System.out.println("w = \"" + w + "\"");
                System.out.println("Resultado: " + (aceptada ? "SI" : "NO"));

                numero++;
            }

            scanner.close();

        } catch (IOException e) {
            System.out.println("No fue posible leer el archivo '" + archivo + "'.");
            System.out.println("Asegurese de que expresiones.txt este en la carpeta del proyecto.");
        }
    }

    // ==========================================================
    // SHUNTING YARD
    // ==========================================================

    public static String convertirPostfix(String expresion) {
        Stack<Character> pila = new Stack<>();
        StringBuilder salida = new StringBuilder();

        for (int i = 0; i < expresion.length(); i++) {
            char c = expresion.charAt(i);

            // Caracter escapado: \+ , \* , etc.
            if (c == '\\') {
                if (i + 1 < expresion.length()) {
                    salida.append(c).append(expresion.charAt(++i));
                }
                continue;
            }

            if (esOperando(c)) {
                salida.append(c);

            } else if (c == '(') {
                pila.push(c);

            } else if (c == ')') {
                while (!pila.isEmpty() && pila.peek() != '(')
                    salida.append(pila.pop());

                if (!pila.isEmpty())
                    pila.pop();

            } else {
                while (!pila.isEmpty()
                        && pila.peek() != '('
                        && prioridad(pila.peek()) >= prioridad(c)) {
                    salida.append(pila.pop());
                }
                pila.push(c);
            }
        }

        while (!pila.isEmpty())
            salida.append(pila.pop());

        return salida.toString();
    }

    public static int prioridad(char c) {
        switch (c) {
            case '*':
            case '+':
            case '?':
                return 3;
            case '.':
                return 2;
            case '|':
                return 1;
            default:
                return 0;
        }
    }

    // ε se considera operando porque representa la cadena vacia.
    public static boolean esOperando(char c) {
        return Character.isLetterOrDigit(c)
                || c == '_'
                || c == 'ε'
                || c == '['
                || c == ']';
    }

    // ==========================================================
    // EXPANSION DE + Y ?
    // ==========================================================

    public static String expandirOperadores(String expresion) {
        StringBuilder resultado = new StringBuilder();

        for (int i = 0; i < expresion.length(); i++) {
            char c = expresion.charAt(i);

            if (c == '\\') {
                resultado.append(c);
                if (i + 1 < expresion.length())
                    resultado.append(expresion.charAt(++i));
                continue;
            }

            // X+ = XX*
            if (c == '+') {
                if (resultado.length() > 0) {
                    String unidad = extraerUltimaUnidad(resultado);
                    resultado.append(unidad).append(unidad).append('*');
                }
                continue;
            }

            // X? = (X|ε)
            if (c == '?') {
                if (resultado.length() > 0) {
                    String unidad = extraerUltimaUnidad(resultado);
                    resultado.append('(').append(unidad).append('|')
                            .append(EPSILON).append(')');
                }
                continue;
            }

            resultado.append(c);
        }

        return resultado.toString();
    }

    public static String extraerUltimaUnidad(StringBuilder resultado) {
        int len = resultado.length();
        if (len == 0) return "";

        char ultimo = resultado.charAt(len - 1);

        // Grupo balanceado.
        if (ultimo == ')') {
            int balance = 0;
            int idx = len - 1;

            for (; idx >= 0; idx--) {
                char ch = resultado.charAt(idx);
                if (ch == ')') balance++;
                else if (ch == '(') balance--;

                if (balance == 0) break;
            }

            String unidad = resultado.substring(idx, len);
            resultado.delete(idx, len);
            return unidad;
        }

        // Caracter escapado.
        if (len >= 2 && resultado.charAt(len - 2) == '\\') {
            String unidad = resultado.substring(len - 2, len);
            resultado.delete(len - 2, len);
            return unidad;
        }

        String unidad = String.valueOf(ultimo);
        resultado.deleteCharAt(len - 1);
        return unidad;
    }

    // ==========================================================
    // CONCATENACION IMPLICITA -> .
    // ==========================================================

    public static String insertarConcatenacion(String expresion) {
        StringBuilder resultado = new StringBuilder();

        for (int i = 0; i < expresion.length(); i++) {
            char actual = expresion.charAt(i);
            resultado.append(actual);

            if (i == expresion.length() - 1) continue;

            char siguiente = expresion.charAt(i + 1);

            if (debeConcatenar(actual, siguiente))
                resultado.append('.');
        }

        return resultado.toString();
    }

    public static boolean debeConcatenar(char a, char b) {
        boolean primero = esOperando(a)
                || a == ')'
                || a == '*'
                || a == '+'
                || a == '?';

        boolean segundo = esOperando(b)
                || b == '('
                || b == '\\';

        return primero && segundo;
    }

    // ==========================================================
    // ARBOL SINTACTICO
    // ==========================================================

    public static class Nodo {
        String valor;
        Nodo izq;
        Nodo der;

        public Nodo(String valor) {
            this.valor = valor;
        }

        public Nodo(String valor, Nodo unico) {
            this.valor = valor;
            this.izq = unico;
        }

        public Nodo(String valor, Nodo izq, Nodo der) {
            this.valor = valor;
            this.izq = izq;
            this.der = der;
        }

        public boolean esHoja() {
            return izq == null && der == null;
        }
    }

    public static Nodo construirArbolSintactico(String postfix) {
        Stack<Nodo> pila = new Stack<>();

        for (String token : tokenizarPostfix(postfix)) {
            if (token.equals(".") || token.equals("|")) {
                Nodo derecho = pila.pop();
                Nodo izquierdo = pila.pop();
                pila.push(new Nodo(token, izquierdo, derecho));

            } else if (token.equals("*")) {
                Nodo hijo = pila.pop();
                pila.push(new Nodo(token, hijo));

            } else {
                pila.push(new Nodo(token));
            }
        }

        return pila.isEmpty() ? null : pila.pop();
    }

    public static List<String> tokenizarPostfix(String postfix) {
        List<String> tokens = new ArrayList<>();

        for (int i = 0; i < postfix.length(); i++) {
            char c = postfix.charAt(i);

            if (c == '\\' && i + 1 < postfix.length()) {
                tokens.add("" + c + postfix.charAt(++i));
            } else {
                tokens.add(String.valueOf(c));
            }
        }

        return tokens;
    }

    public static void imprimirArbol(Nodo nodo, String prefijo, boolean esUltimo) {
        if (nodo == null) return;

        System.out.println(prefijo
                + (esUltimo ? "└── " : "├── ")
                + nodo.valor);

        String nuevoPrefijo = prefijo + (esUltimo ? "    " : "│   ");

        List<Nodo> hijos = new ArrayList<>();
        if (nodo.izq != null) hijos.add(nodo.izq);
        if (nodo.der != null) hijos.add(nodo.der);

        for (int i = 0; i < hijos.size(); i++)
            imprimirArbol(hijos.get(i), nuevoPrefijo, i == hijos.size() - 1);
    }

    // ==========================================================
    // AFN DE THOMPSON
    // ==========================================================

    public static class Estado {
        int id;
        Map<String, List<Estado>> transiciones = new LinkedHashMap<>();

        Estado() {
            id = nextStateId++;
        }

        void agregarTransicion(String simbolo, Estado destino) {
            transiciones.computeIfAbsent(simbolo, k -> new ArrayList<>())
                    .add(destino);
        }
    }

    public static class Fragmento {
        Estado inicio;
        Estado fin;

        Fragmento(Estado inicio, Estado fin) {
            this.inicio = inicio;
            this.fin = fin;
        }
    }

    /**
     * Construye el AFN siguiendo directamente las reglas de Thompson:
     *
     * a:        i --a--> f
     * ε:        i --ε--> f
     * XY:       fX --ε--> iY
     * X|Y:      i --ε--> iX, i --ε--> iY
     * X*:       i --ε--> f, i --ε--> iX,
     *           fX --ε--> iX, fX --ε--> f
     */
    public static Fragmento thompson(Nodo nodo) {
        if (nodo == null)
            return null;

        // Hoja.
        if (nodo.esHoja()) {
            Estado inicio = new Estado();
            Estado fin = new Estado();

            String simbolo = nodo.valor.equals(EPSILON)
                    ? EPSILON
                    : limpiarToken(nodo.valor);

            inicio.agregarTransicion(simbolo, fin);
            return new Fragmento(inicio, fin);
        }

        // Union X|Y
        if (nodo.valor.equals("|")) {
            Fragmento izq = thompson(nodo.izq);
            Fragmento der = thompson(nodo.der);

            Estado inicio = new Estado();
            Estado fin = new Estado();

            inicio.agregarTransicion(EPSILON, izq.inicio);
            inicio.agregarTransicion(EPSILON, der.inicio);

            izq.fin.agregarTransicion(EPSILON, fin);
            der.fin.agregarTransicion(EPSILON, fin);

            return new Fragmento(inicio, fin);
        }

        // Concatenacion XY
        if (nodo.valor.equals(".")) {
            Fragmento izq = thompson(nodo.izq);
            Fragmento der = thompson(nodo.der);

            izq.fin.agregarTransicion(EPSILON, der.inicio);

            return new Fragmento(izq.inicio, der.fin);
        }

        // Cerradura X*
        if (nodo.valor.equals("*")) {
            Fragmento hijo = thompson(nodo.izq);

            Estado inicio = new Estado();
            Estado fin = new Estado();

            inicio.agregarTransicion(EPSILON, fin);
            inicio.agregarTransicion(EPSILON, hijo.inicio);

            hijo.fin.agregarTransicion(EPSILON, hijo.inicio);
            hijo.fin.agregarTransicion(EPSILON, fin);

            return new Fragmento(inicio, fin);
        }

        throw new IllegalArgumentException("Operador no reconocido: " + nodo.valor);
    }

    private static String limpiarToken(String token) {
        if (token.startsWith("\\") && token.length() == 2)
            return String.valueOf(token.charAt(1));
        return token;
    }

    // ==========================================================
    // SIMULACION DEL AFN
    // ==========================================================

    public static boolean simularAFN(Estado inicio, Estado aceptacion, String w) {
        Set<Estado> actuales = epsilonCierre(
                Collections.singleton(inicio));

        System.out.println("\nSIMULACION:");
        System.out.println("Inicio: " + estadosComoTexto(actuales));

        for (int i = 0; i < w.length(); i++) {
            String simbolo = String.valueOf(w.charAt(i));

            Set<Estado> siguientes = mover(actuales, simbolo);
            actuales = epsilonCierre(siguientes);

            System.out.println("Despues de leer '" + simbolo + "': "
                    + estadosComoTexto(actuales));
        }

        return actuales.contains(aceptacion);
    }

    public static Set<Estado> mover(Set<Estado> estados, String simbolo) {
        Set<Estado> resultado = new LinkedHashSet<>();

        for (Estado estado : estados) {
            List<Estado> destinos = estado.transiciones.get(simbolo);
            if (destinos != null)
                resultado.addAll(destinos);
        }

        return resultado;
    }

    public static Set<Estado> epsilonCierre(Collection<Estado> iniciales) {
        Set<Estado> cierre = new LinkedHashSet<>(iniciales);
        Stack<Estado> pila = new Stack<>();
        pila.addAll(iniciales);

        while (!pila.isEmpty()) {
            Estado actual = pila.pop();
            List<Estado> destinos = actual.transiciones.get(EPSILON);

            if (destinos == null) continue;

            for (Estado destino : destinos) {
                if (cierre.add(destino))
                    pila.push(destino);
            }
        }

        return cierre;
    }

    private static String estadosComoTexto(Set<Estado> estados) {
        if (estados.isEmpty()) return "∅";

        StringBuilder sb = new StringBuilder("{");
        boolean primero = true;

        for (Estado e : estados) {
            if (!primero) sb.append(", ");
            sb.append("q").append(e.id);
            primero = false;
        }

        sb.append("}");
        return sb.toString();
    }

    // ==========================================================
    // IMPRIMIR AFN EN CONSOLA
    // ==========================================================

    public static void imprimirAFN(Estado inicio) {
        Set<Estado> visitados = new LinkedHashSet<>();
        imprimirAFNRec(inicio, visitados);
    }

    private static void imprimirAFNRec(Estado estado, Set<Estado> visitados) {
        if (!visitados.add(estado)) return;

        for (Map.Entry<String, List<Estado>> entrada :
                estado.transiciones.entrySet()) {

            for (Estado destino : entrada.getValue()) {
                System.out.println("q" + estado.id
                        + " --" + entrada.getKey()
                        + "--> q" + destino.id);

                imprimirAFNRec(destino, visitados);
            }
        }
    }

    // ==========================================================
    // DIBUJO GRAFICO DEL AFN
    // ==========================================================

    public static void mostrarAFNGrafico(Estado inicio, Estado aceptacion,
                                         String titulo) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(titulo);
            PanelAFN panel = new PanelAFN(inicio, aceptacion);

            frame.add(new JScrollPane(panel));
            frame.setSize(1100, 700);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setVisible(true);
        });
    }

    public static class PanelAFN extends JPanel {
        private final Estado inicio;
        private final Estado aceptacion;
        private final List<Estado> estados = new ArrayList<>();
        private final Map<Estado, Point> posiciones = new LinkedHashMap<>();

        private static final int RADIO = 24;
        private static final int SEP_X = 110;
        private static final int SEP_Y = 90;

        PanelAFN(Estado inicio, Estado aceptacion) {
            this.inicio = inicio;
            this.aceptacion = aceptacion;

            recolectarEstados(inicio, new LinkedHashSet<>());
            calcularPosiciones();

            int ancho = Math.max(1050, estados.size() * SEP_X + 150);
            setPreferredSize(new Dimension(ancho, 600));
            setBackground(Color.WHITE);
        }

        private void recolectarEstados(Estado estado, Set<Estado> visitados) {
            if (!visitados.add(estado)) return;

            estados.add(estado);

            for (List<Estado> destinos : estado.transiciones.values())
                for (Estado destino : destinos)
                    recolectarEstados(destino, visitados);
        }

        private void calcularPosiciones() {
            /*
             * Se colocan los estados en filas. No intenta ser un layout
             * matematicamente perfecto, pero permite visualizar claramente
             * todos los estados y transiciones del AFN.
             */
            int columnas = Math.max(1, Math.min(9, estados.size()));
            int ancho = Math.max(SEP_X, columnas * SEP_X);

            for (int i = 0; i < estados.size(); i++) {
                int fila = i / columnas;
                int columna = i % columnas;

                int x = 70 + columna * SEP_X;
                int y = 100 + fila * 130;

                posiciones.put(estados.get(i), new Point(x, y));
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            dibujarTransiciones(g2);
            dibujarNodos(g2);
        }

        private void dibujarTransiciones(Graphics2D g2) {
            Set<String> dibujadas = new HashSet<>();

            for (Estado origen : estados) {
                Point p = posiciones.get(origen);

                for (Map.Entry<String, List<Estado>> entrada :
                        origen.transiciones.entrySet()) {

                    String simbolo = entrada.getKey();

                    for (Estado destino : entrada.getValue()) {
                        Point q = posiciones.get(destino);

                        String clave = origen.id + "-" + destino.id
                                + "-" + simbolo;

                        if (dibujadas.add(clave))
                            dibujarFlecha(g2, p, q, simbolo);
                    }
                }
            }
        }

        private void dibujarFlecha(Graphics2D g2, Point a, Point b,
                                    String simbolo) {

            if (a.equals(b)) {
                int loop = 35;

                g2.drawArc(a.x - loop, a.y - 2 * loop,
                        2 * loop, 2 * loop, 30, 290);

                g2.drawString(simbolo, a.x - 5, a.y - 2 * loop - 8);
                return;
            }

            double dx = b.x - a.x;
            double dy = b.y - a.y;
            double distancia = Math.sqrt(dx * dx + dy * dy);

            double ux = dx / distancia;
            double uy = dy / distancia;

            int x1 = (int) (a.x + ux * RADIO);
            int y1 = (int) (a.y + uy * RADIO);

            int x2 = (int) (b.x - ux * RADIO);
            int y2 = (int) (b.y - uy * RADIO);

            g2.drawLine(x1, y1, x2, y2);

            int tam = 8;
            int px = (int) (x2 - tam * ux + tam * uy);
            int py = (int) (y2 - tam * uy - tam * ux);
            int qx = (int) (x2 - tam * ux - tam * uy);
            int qy = (int) (y2 - tam * uy + tam * ux);

            Polygon punta = new Polygon();
            punta.addPoint(x2, y2);
            punta.addPoint(px, py);
            punta.addPoint(qx, qy);
            g2.fillPolygon(punta);

            int tx = (x1 + x2) / 2;
            int ty = (y1 + y2) / 2 - 7;

            g2.drawString(simbolo, tx, ty);
        }

        private void dibujarNodos(Graphics2D g2) {
            for (Estado estado : estados) {
                Point p = posiciones.get(estado);

                if (estado == aceptacion) {
                    g2.drawOval(p.x - RADIO - 5, p.y - RADIO - 5,
                            2 * (RADIO + 5), 2 * (RADIO + 5));
                }

                if (estado == inicio) {
                    g2.drawLine(p.x - 55, p.y, p.x - RADIO, p.y);

                    Polygon punta = new Polygon();
                    punta.addPoint(p.x - RADIO, p.y);
                    punta.addPoint(p.x - RADIO - 10, p.y - 6);
                    punta.addPoint(p.x - RADIO - 10, p.y + 6);
                    g2.fillPolygon(punta);
                }

                g2.fillOval(p.x - RADIO, p.y - RADIO,
                        RADIO * 2, RADIO * 2);

                g2.setColor(Color.BLACK);
                g2.drawOval(p.x - RADIO, p.y - RADIO,
                        RADIO * 2, RADIO * 2);

                String texto = "q" + estado.id;
                FontMetrics fm = g2.getFontMetrics();

                g2.drawString(texto,
                        p.x - fm.stringWidth(texto) / 2,
                        p.y + fm.getAscent() / 2 - 2);
            }
        }
    }
}