package edu.unlam.tpa_COMUNICACION;

import java.awt.Color;
import java.awt.Font;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;

import edu.unlam.tpa_PAQUETESCLIENTE.PaqueteSala;
import edu.unlam.tpa_UTILES.BaseDeDatos;
import edu.unlam.tpa_UTILES.HiloPartida;




public class Servidor extends Thread {

	public static ArrayList<Socket> SocketsConectados = new ArrayList<Socket>();
	public static ArrayList<String> UsuariosConectados = new ArrayList<String>();
	private static ArrayList<EscuchaCliente> clientesConectados = new ArrayList<>();
	public static Map<String, Socket> mapConectados = new HashMap<>();
	public static ArrayList<HiloPartida> partidas = new ArrayList<>();
	public static ArrayList<String> salasNombresDisponibles = new ArrayList<String>();
	

	public static Map<String, PaqueteSala> salas = new HashMap<>();
	
	private static ServerSocket serverSocket;
	private final int puerto = 1234;
	private static BaseDeDatos conexionDB;

	private static Thread server;

	static TextArea log = new TextArea();
	static boolean estadoServer;

	public static AtencionConexiones atencionConexiones;
	public static AtencionNuevasSalas atencionNuevasSalas;
	public static AtencionConexionesSalas atencionConexionesSalas;

	public static void main(String[] args) {
		cargarInterfaz();
	}

	private static void cargarInterfaz() {
		JFrame ventana = new JFrame("Servidor del Chat");
		ventana.setTitle("Servidor");
		ventana.getContentPane().setFont(new Font("Arial", Font.PLAIN, 11));
		ventana.getContentPane().setBackground(Color.DARK_GRAY);
		ventana.getContentPane().setForeground(Color.GRAY);
		ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		ventana.setSize(541, 538);
		ventana.setResizable(false);
		ventana.setLocationRelativeTo(null);
		ventana.getContentPane().setLayout(null);

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(20, 74, 485, 424);
		ventana.getContentPane().add(scrollPane);
		getLog().setFont(new Font("Arial", Font.PLAIN, 14));
		getLog().setBackground(Color.BLACK);
		getLog().setForeground(Color.WHITE);
		getLog().setEditable(false);

		scrollPane.setRowHeaderView(getLog());

		final JButton btnIniciar = new JButton();
		btnIniciar.setForeground(Color.WHITE);
		btnIniciar.setBounds(32, 23, 165, 23);
		btnIniciar.setFont(new Font("Verdana", Font.PLAIN, 11));
		btnIniciar.setBackground(Color.BLACK);
		final JButton btnParar = new JButton();
		btnParar.setForeground(Color.WHITE);
		btnParar.setBounds(345, 23, 160, 23);
		btnParar.setFont(new Font("Verdana", Font.PLAIN, 11));
		btnParar.setBackground(Color.BLACK);
		btnIniciar.setText("Iniciar Servidor");
		btnIniciar.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				server = new Thread(new Servidor());
				server.start();
				btnIniciar.setEnabled(false);
				btnParar.setEnabled(true);
				ventana.getRootPane().setDefaultButton(btnParar);
				btnIniciar.requestFocus();
			}
		});


		ventana.getContentPane().add(btnIniciar);
		ventana.getRootPane().setDefaultButton(btnIniciar);
		btnIniciar.requestFocus();

		btnParar.setText("Parar Servidor");
		btnParar.addActionListener(new ActionListener() {
			@SuppressWarnings("deprecation")
			public void actionPerformed(ActionEvent e) {
				try {
					estadoServer = false;
					UsuariosConectados = new ArrayList<String>();
					server.stop();
					atencionConexiones.stop();
					for (EscuchaCliente cliente : clientesConectados) {
						cliente.getSalida().close();
						cliente.getEntrada().close();
						cliente.getSocket().close();
					}
					serverSocket.close();
					conexionDB.cerrarFactory();
					getLog().append("El servidor se ha detenido." + System.lineSeparator());
				} catch (Exception e1) {
					getLog().append("Fallo al intentar detener el servidor." + System.lineSeparator());
					e1.printStackTrace();
				}
				btnParar.setEnabled(false);
				btnIniciar.setEnabled(true);
				ventana.getRootPane().setDefaultButton(btnIniciar);
				btnIniciar.requestFocus();
			}
		});
		btnParar.setEnabled(false);
		ventana.getContentPane().add(btnParar);
		ventana.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		ventana.addWindowListener(new WindowAdapter() {
			@SuppressWarnings("deprecation")
			public void windowClosing(WindowEvent evt) {
				if (serverSocket != null) {
					try {				
						estadoServer = false;
						UsuariosConectados = new ArrayList<String>();
						server.stop();
						atencionConexiones.stop();
						for (EscuchaCliente cliente : clientesConectados) {
							cliente.getSalida().close();
							cliente.getEntrada().close();
							cliente.getSocket().close();
						}
						serverSocket.close();
						conexionDB.cerrarFactory();
					} catch (IOException e) {
						getLog().append("Fallo al intentar detener el servidor." + System.lineSeparator());
						e.printStackTrace();
						System.exit(1);
					}
				}
				System.exit(0);
			}
		});
		ventana.setVisible(true);
	}


	@Override
	public void run() {
		try {
			getLog().append("Cargando Base de Datos..." + System.lineSeparator());
			conexionDB = new BaseDeDatos();
			conexionDB.iniciarBd();
			
			estadoServer = true;
			getLog().append("Iniciando el servidor..." + System.lineSeparator());			
			serverSocket = new ServerSocket(puerto);
			getLog().append("Servidor esperando conexiones..." + System.lineSeparator());
			String ipRemota;		
			

			atencionConexiones = new AtencionConexiones();
			atencionConexiones.start();
			atencionNuevasSalas = new AtencionNuevasSalas();
			atencionNuevasSalas.start();
			atencionConexionesSalas = new AtencionConexionesSalas();
			atencionConexionesSalas.start();


			while (estadoServer) {
				Socket cliente = serverSocket.accept();
				SocketsConectados.add(cliente);
				
				ipRemota = cliente.getInetAddress().getHostAddress();
				getLog().append(ipRemota + " se ha conectado" + System.lineSeparator());

				ObjectOutputStream salida = new ObjectOutputStream(cliente.getOutputStream());
				ObjectInputStream entrada = new ObjectInputStream(cliente.getInputStream());

				EscuchaCliente atencion = new EscuchaCliente(ipRemota, cliente, entrada, salida);
				atencion.start();
				clientesConectados.add(atencion);
			}
		} catch (Exception e) {
			getLog().append("Fallo la conexi�n." + System.lineSeparator());
			e.printStackTrace();
		}
	}

	public static Map<String, Socket> getMapConectados() {
		return mapConectados;
	}

	public static void setMapConectados(Map<String, Socket> mapConectados) {
		Servidor.mapConectados = mapConectados;
	}

	public static AtencionConexiones getAtencionConexiones() {
		return atencionConexiones;
	}

	public static void setAtencionConexiones(AtencionConexiones atencionConexiones) {
		Servidor.atencionConexiones = atencionConexiones;
	}

	public static AtencionNuevasSalas getAtencionNuevasSalas() {
		return atencionNuevasSalas;
	}

	public static void setAtencionNuevasSalas(AtencionNuevasSalas atencionNuevasSalas) {
		Servidor.atencionNuevasSalas = atencionNuevasSalas;
	}

	public static AtencionConexionesSalas getAtencionConexionesSalas() {
		return atencionConexionesSalas;
	}

	public static void setAtencionConexionesSalas(AtencionConexionesSalas atencionConexionesSalas) {
		Servidor.atencionConexionesSalas = atencionConexionesSalas;
	}

	public static ArrayList<EscuchaCliente> getClientesConectados() {
		return clientesConectados;
	}

	public static void setClientesConectados(ArrayList<EscuchaCliente> clientesConectados) {
		Servidor.clientesConectados = clientesConectados;
	}

	public static ArrayList<String> getUsuariosConectados() {
		return UsuariosConectados;
	}

	public static ArrayList<Socket> getSocketsConectados() {
		return SocketsConectados;
	}

	public static void setSocketsConectados(ArrayList<Socket> socketsConectados) {
		SocketsConectados = socketsConectados;
	}

	public static ArrayList<String> getNombresSalasDisponibles() {
		return salasNombresDisponibles;
	}

	public static void setNombresSalasDisponibles(ArrayList<String> salasDisponibles) {
		Servidor.salasNombresDisponibles = salasDisponibles;
	}

	public static boolean mensajeAAll(int contador) {
		boolean result = true;
		if(UsuariosConectados.size() != contador+1) {
			result = false;
		}
		if (result) {
			Servidor.getLog().append("Se ha enviado un mensaje general" + System.lineSeparator());
			return true;
		} else {
			Servidor.getLog().append("Se ha desconectado un usuario" + System.lineSeparator());
			return false;
		}
	}

	public static Map<String, Socket> getPersonajesConectados() {
		return mapConectados;
	}

	public static void setPersonajesConectados(Map<String, Socket> personajesConectados) {
		Servidor.mapConectados = personajesConectados;
	}

	public static BaseDeDatos getConector() {
		return conexionDB;
	}

	public static Map<String, PaqueteSala> getSalas() {
		return salas;
	}


	public static TextArea getLog() {
		return log;
	}

	public static void setLog(TextArea log) {
		Servidor.log = log;
	}

	public static void eliminarSalaDisponible(String nombreSala) {
		salasNombresDisponibles.remove(nombreSala);
		salas.remove(nombreSala);
	}

	public static void conectarUsuario(String username) {
		UsuariosConectados.add(username);
		int index = Servidor.getUsuariosConectados().indexOf(username);
		mapConectados.put(username, SocketsConectados.get(index));
	}

	public static void agregarSalaDisponible(PaqueteSala paqueteSala) {
		salasNombresDisponibles.add(paqueteSala.getNombreSala());
		salas.put(paqueteSala.getNombreSala(), paqueteSala);
	}

	public static void desconectarUsuario(String username, EscuchaCliente escuchaCliente) {
		int index = Servidor.UsuariosConectados.indexOf(username);
		SocketsConectados.remove(index);
		mapConectados.remove(username);
		UsuariosConectados.remove(username);
		clientesConectados.remove(escuchaCliente);
	}

	public static void addPartida(HiloPartida hiloPartida) {
		partidas.add(hiloPartida);
	}

	public static void modificarSalaDisponible(String nombreSala) {
				
		salas.get(nombreSala).setEnJuego();;
			
	}
}