package edu.unlam.tpa_UTILES;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.Gson;

import edu.unlam.tpa_COMUNICACION.EscuchaCliente;
import edu.unlam.tpa_ENUMS.Velocidad;
import edu.unlam.tpa_JUEGO.Direccion;
import edu.unlam.tpa_JUEGO.Fruta;
import edu.unlam.tpa_JUEGO.Mapa;
import edu.unlam.tpa_JUEGO.Partida;
import edu.unlam.tpa_JUEGO.Posicion;
import edu.unlam.tpa_JUEGO.Snake;
import edu.unlam.tpa_PAQUETESCLIENTE.Comando;
import edu.unlam.tpa_PAQUETESCLIENTE.PaquetePartida;

public class HiloPartida extends Thread {

	private Mapa mapa;
	private Partida partida;
	private ArrayList<Jugador> jugadores;
	private ArrayList<Fruta> frutas = new ArrayList<>();
	private ArrayList<Snake> listaSnakes;
	public static ArrayList<Color> colores;
	private Velocidad speed = Velocidad.NORMAL;
	private PaquetePartida paquetePartida;


	private ArrayList<EscuchaCliente> clientes;

	public void cargarSnakes() {
		/**
		 * Hasta 12 snakes para dividir bien el mapa, arrancarian 3 en cada borde. Si
		 * son menos se le elige una posicion random de estas.
		 */
		listaSnakes = new ArrayList<>();
		listaSnakes.add(new Snake(2, 5, Direccion.DERECHA));
		listaSnakes.add(new Snake(2, 10, Direccion.DERECHA));
		listaSnakes.add(new Snake(2, 15, Direccion.DERECHA));

		listaSnakes.add(new Snake(5, 2, Direccion.ABAJO));
		listaSnakes.add(new Snake(10, 2, Direccion.ABAJO));
		listaSnakes.add(new Snake(15, 2, Direccion.ABAJO));

		listaSnakes.add(new Snake(18, 5, Direccion.IZQUIERDA));
		listaSnakes.add(new Snake(18, 10, Direccion.IZQUIERDA));
		listaSnakes.add(new Snake(18, 15, Direccion.IZQUIERDA));

		listaSnakes.add(new Snake(5, 18, Direccion.ARRIBA));
		listaSnakes.add(new Snake(10, 18, Direccion.ARRIBA));
		listaSnakes.add(new Snake(15, 18, Direccion.ARRIBA));
	}

	public static void cargarColores() {
		colores = new ArrayList<>();
		colores.add(Color.ORANGE);
		colores.add(Color.BLUE);
		colores.add(Color.RED);
		colores.add(Color.YELLOW);
		colores.add(Color.CYAN);
		colores.add(Color.MAGENTA);
		colores.add(Color.PINK);
		colores.add(new Color(120, 40, 140));// Violeta
		colores.add(new Color(182, 149, 192));// Lila
		colores.add(new Color(234, 190, 63));// Dorado
		colores.add(Color.WHITE);
		colores.add(Color.LIGHT_GRAY);
	}

	public Snake obtenerSnakeEnPosRandom() {
		int randomNum = ThreadLocalRandom.current().nextInt(0, listaSnakes.size());
		Snake snakeAux = listaSnakes.get(randomNum);
		listaSnakes.remove(randomNum);
		return snakeAux;
	}

	public HiloPartida(ArrayList<Jugador> jugadores, ArrayList<EscuchaCliente> clientesJugando) {
		this.jugadores = jugadores;
		this.clientes = clientesJugando;
		frutas.add(new Fruta(9, 9));
		mapa = new Mapa(20, 20);
		partida = new Partida(mapa);
		cargarSnakes();
		cargarColores();
		int i = 0;
		for (Jugador jugador : jugadores) {
			Snake s = obtenerSnakeEnPosRandom();
			// Setear color random tambien
			jugador.setSnake(s);
			jugador.setColor(colores.get(i++));
			partida.addSnake(s);
		}
		for (Fruta fruta : frutas) {
			partida.addFruta(fruta);
		}

		paquetePartida = new PaquetePartida(jugadores, obtenerFrutas(), obtenerSnakes(), 20);
	}

	public boolean masDeUnaEstaViva() {
		int i = 0;
		for (Jugador jugador : jugadores) {
			if (jugador.getSnake().estaViva())
				i++;
			if (i > 1)
				return true;
		}
		return false;
	}

	public Direccion teclaAdireccion(int tecla) {
		switch (tecla) {
		case KeyEvent.VK_A:
			return Direccion.IZQUIERDA;
		case KeyEvent.VK_S:
			return Direccion.ABAJO;
		case KeyEvent.VK_D:
			return Direccion.DERECHA;
		case KeyEvent.VK_W:
			return Direccion.ARRIBA;
		}
		return null;
	}

	public void actualizarDirecciones() {
		for (Jugador jugador : jugadores) {
			Snake s = jugador.getSnake();
			Direccion dirAux;
			if ((dirAux = teclaAdireccion(jugador.getUltimaTeclaPresionada())) != null)
				s.cambiarDireccion(dirAux);
		}
	}

	public void actualizarPuntos() {
		for (Jugador jugador : jugadores) {
			Snake s = jugador.getSnake();
			if ("crecio".equalsIgnoreCase(s.getEstado())) {
				jugador.sumarPuntos(10);
			}
		}
	}

	@Override
	public void run() {
		synchronized (this) {
			Gson gson = new Gson();
			while (masDeUnaEstaViva()) {
				actualizarDirecciones();
				partida.actualizarPartida();
				actualizarPuntos();
				try {
					Thread.sleep(1000 / speed.getValor());
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				paquetePartida.setPaquete(jugadores, obtenerFrutas(), obtenerSnakes());
				paquetePartida.setComando(Comando.PAINT);

				for (EscuchaCliente conectado : clientes) {
					try {
						conectado.getSalida().writeObject(gson.toJson(paquetePartida));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			// Avisaar que termino
		}
	}

	public ArrayList<Posicion> obtenerFrutas() {
		return ObtenedorDePuntos.obtenerPuntosFrutas(this.mapa.getFrutas());
	}

	public Map<Color, ArrayList<Posicion>> obtenerSnakes() {
		Map<Color, ArrayList<Posicion>> map = new HashMap<>();
		for (Jugador jugador : jugadores) {
			map.put(jugador.getColor(), ObtenedorDePuntos.obtenedorDePuntosSnake(jugador.getSnake()));
		}
		return map;
	}
	
	public PaquetePartida getPaquetePartida() {
		return paquetePartida;
	}

}
