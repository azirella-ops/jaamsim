/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2002-2011 Ausenco Engineering Canada Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.jaamsim.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JWindow;
import javax.swing.SpinnerNumberModel;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.jaamsim.basicsim.Entity;
import com.jaamsim.basicsim.ErrorException;
import com.jaamsim.basicsim.Simulation;
import com.jaamsim.controllers.RateLimiter;
import com.jaamsim.controllers.RenderManager;
import com.jaamsim.events.EventErrorListener;
import com.jaamsim.events.EventManager;
import com.jaamsim.events.EventTimeListener;
import com.jaamsim.input.Input;
import com.jaamsim.input.InputAgent;
import com.jaamsim.input.InputErrorException;
import com.jaamsim.input.KeywordIndex;
import com.jaamsim.input.Parser;
import com.jaamsim.math.Vec3d;
import com.jaamsim.units.DistanceUnit;
import com.jaamsim.units.TimeUnit;
import com.jaamsim.units.Unit;

/**
 * The main window for a Graphical Simulation.  It provides the controls for managing then
 * EventManager (run, pause, ...) and the graphics (zoom, pan, ...)
 */
public class GUIFrame extends JFrame implements EventTimeListener, EventErrorListener {
	private static GUIFrame instance;

	// global shutdown flag
	static private AtomicBoolean shuttingDown;

	private JMenu fileMenu;
	private JMenu viewMenu;
	private JMenu windowMenu;
	private JMenu windowList;
	private JMenu optionMenu;
	private JMenu helpMenu;
	private JCheckBoxMenuItem showPosition;
	private JCheckBoxMenuItem alwaysTop;
	//private JCheckBoxMenuItem tooltip;
	private JCheckBoxMenuItem graphicsDebug;
	private JMenuItem printInputItem;
	private JMenuItem saveConfigurationMenuItem;  // "Save"
	private JLabel clockDisplay;
	private JLabel speedUpDisplay;
	private JLabel remainingDisplay;

	private JToggleButton controlRealTime;
	private JSpinner spinner;

	private JToggleButton controlStartResume;
	private JToggleButton controlStop;
	private JTextField pauseTime;

	private JLabel locatorPos;
	private JLabel locatorLabel;

	JButton toolButtonIsometric;
	JButton toolButtonXYPlane;

	private int lastValue = -1;
	private JProgressBar progressBar;
	private static Image iconImage;

	private static final RateLimiter rateLimiter;

	private static boolean SAFE_GRAPHICS;

	// Collection of default window parameters
	public static int COL1_WIDTH;
	public static int COL2_WIDTH;
	public static int COL3_WIDTH;
	public static int COL1_START;
	public static int COL2_START;
	public static int COL3_START;
	public static int HALF_TOP;
	public static int HALF_BOTTOM;
	public static int TOP_START;
	public static int BOTTOM_START;
	public static int LOWER_HEIGHT;
	public static int LOWER_START;
	public static int VIEW_HEIGHT;
	public static int VIEW_WIDTH;

	static {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e) {
			LogBox.logLine("Unable to change look and feel.");
		}

		try {
			URL file = GUIFrame.class.getResource("/resources/images/icon.png");
			iconImage = Toolkit.getDefaultToolkit().getImage(file);
		}
		catch (Exception e) {
			LogBox.logLine("Unable to load icon file.");
			iconImage = null;
		}

		shuttingDown = new AtomicBoolean(false);
		rateLimiter = RateLimiter.create(60);
	}

	private GUIFrame() {
		super();

		getContentPane().setLayout( new BorderLayout() );
		setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );
		this.addWindowListener(new CloseListener());

		// Initialize the working environment
		initializeMenus();
		initializeMainToolBars();
		initializeStatusBar();

		this.setIconImage(GUIFrame.getWindowIcon());

		//Set window size and make visible
		pack();
		setResizable( false );

		controlStartResume.setSelected( false );
		controlStartResume.setEnabled( false );
		controlStop.setSelected( false );
		controlStop.setEnabled( false );
		setProgress( 0 );
		ToolTipManager.sharedInstance().setLightWeightPopupEnabled( false );
		JPopupMenu.setDefaultLightWeightPopupEnabled( false );
	}

	public static synchronized GUIFrame instance() {
		if (instance == null)
			instance = new GUIFrame();

		return instance;
	}

	public static final RateLimiter getRateLimiter() {
		return rateLimiter;
	}

	/**
	 * Listens for window events for the GUI.
	 *
	 */
	private class CloseListener extends WindowAdapter implements ActionListener {
		@Override
		public void windowClosing(WindowEvent e) {
			GUIFrame.this.close();
		}

		@Override
		public void actionPerformed( ActionEvent event ) {
			GUIFrame.this.close();
		}

		@Override
		public void windowDeiconified(WindowEvent e) {

			// Re-open the view windows
			for (View v : View.getAll()) {
				if (v.showWindow())
					RenderManager.inst().createWindow(v);
			}

			// Re-open the tools
			Simulation.showActiveTools();
			FrameBox.reSelectEntity();
		}

		@Override
		public void windowIconified(WindowEvent e) {

			// Close all the tools
			Simulation.closeAllTools();

			// Save whether each window is open or closed
			for (View v : View.getAll()) {
				v.setKeepWindowOpen(v.showWindow());
			}

			// Close all the view windows
			RenderManager.clear();
		}
	}

	/**
	 * Perform exit window duties
	 */
	void close() {
		// check for unsaved changes
		if (InputAgent.isSessionEdited()) {
			boolean confirmed = GUIFrame.showSaveChangesDialog();
			if (!confirmed)
				return;
		}
		InputAgent.closeLogFile();
		GUIFrame.shutdown(0);
	}

	/**
	 * Clears the simulation and user interface for a new run
	 */
	public void clear() {
		currentEvt.clear();
		currentEvt.setTraceListener(null);
		// Clear the simulation
		Simulation.clear();
		FrameBox.clear();
		EntityPallet.clear();
		RenderManager.clear();

		this.updateForSimulationState(GUIFrame.SIM_STATE_LOADED);

		// Clear the title bar
		setTitle(Simulation.getModelName());

		// Clear the status bar
		setProgress( 0 );
		speedUpDisplay.setText("");
		remainingDisplay.setText("");
		locatorPos.setText( "" );

		// Read the autoload configuration file
		InputAgent.clear();
		InputAgent.setRecordEdits(false);
		InputAgent.readResource("inputs/autoload.cfg");
	}

	/**
	 * Sets up the Control Panel's menu bar.
	 */
	public void initializeMenus() {

		// Set up the individual menus
		this.initializeFileMenu();
		this.initializeViewMenu();
		this.initializeWindowMenu();
		this.initializeOptionsMenu();
		this.initializeHelpMenu();

		// Add the individual menu to the main menu
		JMenuBar mainMenuBar = new JMenuBar();
		mainMenuBar.add( fileMenu );
		mainMenuBar.add( viewMenu );
		mainMenuBar.add( windowMenu );
		mainMenuBar.add( optionMenu );
		mainMenuBar.add( helpMenu );

		// Add main menu to the window
		setJMenuBar( mainMenuBar );
	}

	/**
	 * Sets up the File menu in the Control Panel's menu bar.
	 */
	private void initializeFileMenu() {

		// File menu creation
		fileMenu = new JMenu( "File" );
		fileMenu.setMnemonic( 'F' );
		fileMenu.setEnabled( false );

		// 1) "New" menu item
		JMenuItem newMenuItem = new JMenuItem( "New" );
		newMenuItem.setMnemonic( 'N' );
		newMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				currentEvt.pause();

				// check for unsaved changes
				if (InputAgent.isSessionEdited()) {
					boolean confirmed = GUIFrame.showSaveChangesDialog();
					if (!confirmed) {
						return;
					}
				}

				clear();
				InputAgent.setRecordEdits(true);
				InputAgent.loadDefault();
				displayWindows();
			}
		} );
		fileMenu.add( newMenuItem );

		// 2) "Open" menu item
		JMenuItem configMenuItem = new JMenuItem( "Open..." );
		configMenuItem.setMnemonic( 'O' );
		configMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				currentEvt.pause();

				// check for unsaved changes
				if (InputAgent.isSessionEdited()) {
					boolean confirmed = GUIFrame.showSaveChangesDialog();
					if (!confirmed) {
						return;
					}
				}

				GUIFrame.this.load();
			}
		} );
		fileMenu.add( configMenuItem );

		// 3) "Save" menu item
		saveConfigurationMenuItem = new JMenuItem( "Save" );
		saveConfigurationMenuItem.setMnemonic( 'S' );
		saveConfigurationMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				GUIFrame.this.save();
			}
		} );
		fileMenu.add( saveConfigurationMenuItem );

		// 4) "Save As..." menu item
		JMenuItem saveConfigurationAsMenuItem = new JMenuItem( "Save As..." );
		saveConfigurationAsMenuItem.setMnemonic( 'V' );
		saveConfigurationAsMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				GUIFrame.this.saveAs();

			}
		} );
		fileMenu.add( saveConfigurationAsMenuItem );

		// 5) "Import..." menu item
		JMenuItem importGraphicsMenuItem = new JMenuItem( "Import..." );
		importGraphicsMenuItem.setMnemonic( 'I' );
		importGraphicsMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				DisplayEntityFactory.importGraphics(GUIFrame.this);

			}
		} );
		fileMenu.add( importGraphicsMenuItem );

		// 6) "Print Input Report" menu item
		printInputItem = new JMenuItem( "Print Input Report" );
		printInputItem.setMnemonic( 'I' );
		printInputItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				InputAgent.printInputFileKeywords();
			}
		} );
		fileMenu.add( printInputItem );

		// 7) "Exit" menu item
		JMenuItem exitMenuItem = new JMenuItem( "Exit" );
		exitMenuItem.setMnemonic( 'x' );
		exitMenuItem.addActionListener(new CloseListener());
		fileMenu.add( exitMenuItem );
	}

	/**
	 * Sets up the View menu in the Control Panel's menu bar.
	 */
	private void initializeViewMenu() {

		// View menu creation
		viewMenu = new JMenu( "Tools" );
		viewMenu.setMnemonic( 'T' );

		// 1) "Show Basic Tools" menu item
		JMenuItem showBasicToolsMenuItem = new JMenuItem( "Show Basic Tools" );
		showBasicToolsMenuItem.setMnemonic( 'B' );
		showBasicToolsMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				Simulation sim = Simulation.getInstance();
				ArrayList<String> arg = new ArrayList<>(1);
				arg.add("TRUE");
				InputAgent.apply(sim, new KeywordIndex("ShowModelBuilder", arg, null));
				InputAgent.apply(sim, new KeywordIndex("ShowObjectSelector", arg, null));
				InputAgent.apply(sim, new KeywordIndex("ShowInputEditor", arg, null));
				InputAgent.apply(sim, new KeywordIndex("ShowOutputViewer", arg, null));
			}
		} );
		viewMenu.add( showBasicToolsMenuItem );

		// 2) "Close All Tools" menu item
		JMenuItem closeAllToolsMenuItem = new JMenuItem( "Close All Tools" );
		closeAllToolsMenuItem.setMnemonic( 'C' );
		closeAllToolsMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				Simulation sim = Simulation.getInstance();
				ArrayList<String> arg = new ArrayList<>(1);
				arg.add("FALSE");
				InputAgent.apply(sim, new KeywordIndex("ShowModelBuilder", arg, null));
				InputAgent.apply(sim, new KeywordIndex("ShowObjectSelector", arg, null));
				InputAgent.apply(sim, new KeywordIndex("ShowInputEditor", arg, null));
				InputAgent.apply(sim, new KeywordIndex("ShowOutputViewer", arg, null));
				InputAgent.apply(sim, new KeywordIndex("ShowPropertyViewer", arg, null));
				InputAgent.apply(sim, new KeywordIndex("ShowLogViewer", arg, null));
			}
		} );
		viewMenu.add( closeAllToolsMenuItem );

		// 3) "Model Builder" menu item
		JMenuItem objectPalletMenuItem = new JMenuItem( "Model Builder" );
		objectPalletMenuItem.setMnemonic( 'O' );
		objectPalletMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				ArrayList<String> arg = new ArrayList<>(1);
				arg.add("TRUE");
				InputAgent.apply(Simulation.getInstance(), new KeywordIndex("ShowModelBuilder", arg, null));
			}
		} );
		viewMenu.add( objectPalletMenuItem );

		// 4) "Object Selector" menu item
		JMenuItem objectSelectorMenuItem = new JMenuItem( "Object Selector" );
		objectSelectorMenuItem.setMnemonic( 'S' );
		objectSelectorMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				ArrayList<String> arg = new ArrayList<>(1);
				arg.add("TRUE");
				InputAgent.apply(Simulation.getInstance(), new KeywordIndex("ShowObjectSelector", arg, null));
			}
		} );
		viewMenu.add( objectSelectorMenuItem );

		// 5) "Input Editor" menu item
		JMenuItem inputEditorMenuItem = new JMenuItem( "Input Editor" );
		inputEditorMenuItem.setMnemonic( 'I' );
		inputEditorMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				ArrayList<String> arg = new ArrayList<>(1);
				arg.add("TRUE");
				InputAgent.apply(Simulation.getInstance(), new KeywordIndex("ShowInputEditor", arg, null));
			}
		} );
		viewMenu.add( inputEditorMenuItem );

		// 6) "Output Viewer" menu item
		JMenuItem outputMenuItem = new JMenuItem( "Output Viewer" );
		outputMenuItem.setMnemonic( 'U' );
		outputMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				ArrayList<String> arg = new ArrayList<>(1);
				arg.add("TRUE");
				InputAgent.apply(Simulation.getInstance(), new KeywordIndex("ShowOutputViewer", arg, null));
			}
		} );
		viewMenu.add( outputMenuItem );

		// 7) "Property Viewer" menu item
		JMenuItem propertiesMenuItem = new JMenuItem( "Property Viewer" );
		propertiesMenuItem.setMnemonic( 'P' );
		propertiesMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				ArrayList<String> arg = new ArrayList<>(1);
				arg.add("TRUE");
				InputAgent.apply(Simulation.getInstance(), new KeywordIndex("ShowPropertyViewer", arg, null));
			}
		} );
		viewMenu.add( propertiesMenuItem );

		// 8) "Log Viewer" menu item
		JMenuItem logMenuItem = new JMenuItem( "Log Viewer" );
		logMenuItem.setMnemonic( 'L' );
		logMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				ArrayList<String> arg = new ArrayList<>(1);
				arg.add("TRUE");
				InputAgent.apply(Simulation.getInstance(), new KeywordIndex("ShowLogViewer", arg, null));
			}
		} );
		viewMenu.add( logMenuItem );
	}

	/**
	 * Sets up the Window menu in the Control Panel's menu bar.
	 */
	private void initializeWindowMenu() {

		// Window menu creation
		windowMenu = new NewRenderWindowMenu("Views");
		windowMenu.setMnemonic( 'V' );

		// Initialize list of windows
		windowList = new WindowMenu("Select Window");
		windowList.setMnemonic( 'S' );
		//windowMenu.add( windowList );
	}

	/**
	 * Sets up the Options menu in the Control Panel's menu bar.
	 */
	private void initializeOptionsMenu() {

		optionMenu = new JMenu( "Options" );
		optionMenu.setMnemonic( 'O' );

		// 1) "Show Position" check box
		showPosition = new JCheckBoxMenuItem( "Show Position", true );
		showPosition.setMnemonic( 'P' );
		optionMenu.add( showPosition );
		showPosition.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent e ) {
				setShowPositionXY();
			}
		} );

		// 2) "Always on top" check box
		alwaysTop = new JCheckBoxMenuItem( "Always on top", false );
		alwaysTop.setMnemonic( 'A' );
		optionMenu.add( alwaysTop );
		alwaysTop.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				if( GUIFrame.this.isAlwaysOnTop() ) {
					GUIFrame.this.setAlwaysOnTop( false );
				}
				else {
					GUIFrame.this.setAlwaysOnTop( true );
				}
			}
		} );

		/*tooltip = new JCheckBoxMenuItem( "Tooltip", true );
		tooltip.setMnemonic( 'L' );
		optionMenu.add( tooltip );
		tooltip.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				// TODO Needs to be implemented for the new Renderer
			}
		} );*/

		// 3) "Graphics Debug Info" check box
		graphicsDebug = new JCheckBoxMenuItem( "Graphics Debug Info", false );
		graphicsDebug.setMnemonic( 'G' );
		optionMenu.add( graphicsDebug );
		graphicsDebug.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				RenderManager.setDebugInfo(graphicsDebug.getState());
			}
		} );
	}

	/**
	 * Sets up the Help menu in the Control Panel's menu bar.
	 */
	private void initializeHelpMenu() {

		// Help menu creation
		helpMenu = new JMenu( "Help" );
		helpMenu.setMnemonic( 'H' );

		// 1) "About" menu item
		JMenuItem aboutMenu = new JMenuItem( "About" );
		aboutMenu.setMnemonic( 'A' );
		aboutMenu.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				AboutBox.instance().setVisible(true);
			}
		} );
		helpMenu.add( aboutMenu );
	}

	/**
	 * Returns the pixel length of the string with specified font
	 */
	private static int getPixelWidthOfString_ForFont(String str, Font font) {
		FontMetrics metrics = new FontMetrics(font) {};
		Rectangle2D bounds = metrics.getStringBounds(str, null);
		return (int)bounds.getWidth();
	}

	/**
	 * Sets up the Control Panel's main tool bar.
	 */
	public void initializeMainToolBars() {

		// Insets used in setting the toolbar components
		Insets noMargin = new Insets( 0, 0, 0, 0 );
		Insets smallMargin = new Insets( 1, 1, 1, 1 );

		// Initilize the main toolbar
		JToolBar mainToolBar = new JToolBar();
		mainToolBar.setMargin( smallMargin );
		mainToolBar.setFloatable(false);

		// 1) Run/Pause button
		controlStartResume = new JToggleButton(new ImageIcon(GUIFrame.class.getResource("/resources/images/run.png")));
		controlStartResume.setSelectedIcon(
				new ImageIcon(GUIFrame.class.getResource("/resources/images/pause.png")));
		controlStartResume.setToolTipText( "Run" );
		controlStartResume.setMargin( noMargin );
		controlStartResume.setEnabled( false );
		controlStartResume.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				JToggleButton startResume = (JToggleButton)event.getSource();
				startResume.setEnabled(false);
				if(startResume.isSelected()) {
					GUIFrame.this.startSimulation();
				}
				else {
					GUIFrame.this.pauseSimulation();
				}
				controlStartResume.grabFocus();
			}
		} );

		// 2) Stop button
		controlStop = new JToggleButton(new ImageIcon(GUIFrame.class.getResource("/resources/images/stop.png")));
		controlStop.setToolTipText( "Stop" );
		controlStop.setMargin( noMargin );
		controlStop.setEnabled( false );
		controlStop.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				if( getSimState() == SIM_STATE_RUNNING ) {
					GUIFrame.this.pauseSimulation();
				}
				boolean confirmed = GUIFrame.showConfirmStopDialog();
				if (!confirmed)
					return;

				GUIFrame.this.stopSimulation();
				lastSimTime = 0.0d;
				lastSystemTime = System.currentTimeMillis();
				setSpeedUp(0.0d);
			}
		} );

		// Separators have 5 pixels before and after and the preferred height of controlStartResume button
		Dimension separatorDim = new Dimension(11, controlStartResume.getPreferredSize().height);

		// dimension for 5 pixels gaps
		Dimension gapDim = new Dimension(5, separatorDim.height);

		mainToolBar.add( controlStartResume );
		mainToolBar.add(Box.createRigidArea(gapDim));
		mainToolBar.add( controlStop );

		// 3) Pause Time box
		mainToolBar.add(Box.createRigidArea(gapDim));
		JLabel pauseAt = new JLabel( "Pause at:" );
		mainToolBar.add(pauseAt);
		mainToolBar.add(Box.createRigidArea(gapDim));
		pauseTime = new JTextField("2000-00-00") {
			@Override
			protected void processFocusEvent(FocusEvent fe) {
				if (fe.getID() == FocusEvent.FOCUS_LOST) {
					GUIFrame.instance.setPauseTime(this.getText());
				}
				else if (fe.getID() == FocusEvent.FOCUS_GAINED) {
					pauseTime.selectAll();
				}
				super.processFocusEvent( fe );
			}
		};

		// avoid height increase for pauseTime
		pauseTime.setMaximumSize(pauseTime.getPreferredSize());

		// avoid stretching for puaseTime when focusing in and out
		pauseTime.setPreferredSize(pauseTime.getPreferredSize());

		mainToolBar.add(pauseTime);
		pauseTime.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				GUIFrame.instance.setPauseTime(pauseTime.getText());
				controlStartResume.grabFocus();
			}
		});

		pauseTime.setText("");
		pauseTime.setHorizontalAlignment(JTextField.RIGHT);
		pauseTime.setToolTipText( "Time at which to pause the run, e.g. 3 h, 10 s, etc." );

		// 4) Real Time button
		mainToolBar.addSeparator(separatorDim);
		controlRealTime = new JToggleButton( "Real Time" );
		controlRealTime.setToolTipText( "Toggle Real Time mode. When selected, the simulation runs at a fixed multiple of wall clock time." );
		controlRealTime.setMargin( smallMargin );
		controlRealTime.addActionListener(new RealTimeActionListener());

		mainToolBar.add( controlRealTime );
		mainToolBar.add(Box.createRigidArea(gapDim));

		// 5) Speed Up spinner
		SpinnerNumberModel numberModel =
				new SpinnerModel(Simulation.DEFAULT_REAL_TIME_FACTOR,
				   Simulation.MIN_REAL_TIME_FACTOR, Simulation.MAX_REAL_TIME_FACTOR, 1);
		spinner = new JSpinner(numberModel);

		// make sure spinner TextField is no wider than 9 digits
		int diff =
			((JSpinner.DefaultEditor)spinner.getEditor()).getTextField().getPreferredSize().width -
			getPixelWidthOfString_ForFont("9", spinner.getFont()) * 9;
		Dimension dim = spinner.getPreferredSize();
		dim.width -= diff;
		spinner.setMaximumSize(dim);

		spinner.addChangeListener(new SpeedFactorListener());
		spinner.setToolTipText( "Target ratio of simulation time to wall clock time when Real Time mode is selected." );
		mainToolBar.add( spinner );

		// 6) View Control buttons
		mainToolBar.addSeparator(separatorDim);
		JLabel viewLabel = new JLabel( "   View Control:   " );
		mainToolBar.add( viewLabel );

		// 6a) Perspective button
		toolButtonIsometric = new JButton( "Perspective" );
		toolButtonIsometric.setToolTipText( "Set Perspective View" );
		toolButtonIsometric.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				if (RenderManager.isGood())
					RenderManager.inst().setIsometricView();
			}
		} );
		mainToolBar.add( toolButtonIsometric );

		// 6b) XY-Plane button
		toolButtonXYPlane = new JButton( "XY-Plane" );
		toolButtonXYPlane.setToolTipText( "Set XY-Plane View" );
		toolButtonXYPlane.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent event ) {
				if (RenderManager.isGood())
					RenderManager.inst().setXYPlaneView();
			}
		} );
		mainToolBar.add( toolButtonXYPlane );

		// 7) Undo/Redo buttons (not used at present)
		mainToolBar.addSeparator(separatorDim);

		// End creation of view control label and buttons

		// Add toolbar to the window
		getContentPane().add( mainToolBar, BorderLayout.NORTH );
	}

	private static class WindowMenu extends JMenu implements MenuListener {

		WindowMenu(String text) {
			super(text);
			this.addMenuListener(this);
		}

		@Override
		public void menuCanceled(MenuEvent arg0) {}

		@Override
		public void menuDeselected(MenuEvent arg0) {
			this.removeAll();
		}

		@Override
		public void menuSelected(MenuEvent arg0) {
			if (!RenderManager.isGood()) { return; }

			ArrayList<Integer> windowIDs = RenderManager.inst().getOpenWindowIDs();
			for (int id : windowIDs) {
				String windowName = RenderManager.inst().getWindowName(id);
				this.add(new WindowSelector(id, windowName));
			}
		}
	}

	private static class WindowSelector extends JMenuItem implements ActionListener {
		private final int windowID;

		WindowSelector(int windowID, String windowName) {
			this.windowID = windowID;
			this.setText(windowName);
			this.addActionListener(this);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			if (!RenderManager.isGood()) { return; }

			RenderManager.inst().focusWindow(windowID);
		}
	}

	private static class NewRenderWindowMenu extends JMenu implements MenuListener {

		NewRenderWindowMenu(String text) {
			super(text);
			this.addMenuListener(this);
		}

		@Override
		public void menuSelected(MenuEvent e) {

			for (View view : View.getAll()) {
				this.add(new NewRenderWindowLauncher(view));
			}
			this.addSeparator();
			this.add(new ViewDefiner());
		}

		@Override
		public void menuCanceled(MenuEvent arg0) {
		}

		@Override
		public void menuDeselected(MenuEvent arg0) {
			this.removeAll();
		}
	}
	private static class NewRenderWindowLauncher extends JMenuItem implements ActionListener {
		private final View view;

		NewRenderWindowLauncher(View v) {
			view = v;
			this.setText(view.getName());
			this.addActionListener(this);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			if (!RenderManager.isGood()) {
				if (RenderManager.canInitialize()) {
					RenderManager.initialize(SAFE_GRAPHICS);
				} else {
					// A fatal error has occurred, don't try to initialize again
					return;
				}
			}

			ArrayList<String> arg = new ArrayList<>(1);
			arg.add("TRUE");
			InputAgent.apply(view, new KeywordIndex("ShowWindow", arg, null));
			RenderManager.inst().createWindow(view);
			FrameBox.setSelectedEntity(view);
		}
	}

	private static class ViewDefiner extends JMenuItem implements ActionListener {
		ViewDefiner() {} {
			this.setText("Define new View");
			this.addActionListener(this);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			if (!RenderManager.isGood()) {
				if (RenderManager.canInitialize()) {
					RenderManager.initialize(SAFE_GRAPHICS);
				} else {
					// A fatal error has occurred, don't try to initialize again
					return;
				}
			}

			View tmp = InputAgent.defineEntityWithUniqueName(View.class, "View", "", true);
			RenderManager.inst().createWindow(tmp);
			FrameBox.setSelectedEntity(tmp);
			ArrayList<String> arg = new ArrayList<>(1);
			arg.add("TRUE");
			InputAgent.apply(tmp, new KeywordIndex("ShowWindow", arg, null));
		}
	}

	/**
	 * Sets up the Control Panel's status bar.
	 */
	public void initializeStatusBar() {

		// Create the status bar
		JPanel statusBar = new JPanel();
		statusBar.setBorder( BorderFactory.createLineBorder( Color.darkGray ) );
		statusBar.setLayout( new FlowLayout( FlowLayout.LEFT, 10, 5 ) );

		// Create the display clock and label
		JLabel clockLabel = new JLabel( "Simulation Time:" );
		clockDisplay = new JLabel( "", JLabel.RIGHT );
		clockDisplay.setPreferredSize( new Dimension( 85, 16 ) );
		clockDisplay.setForeground( new Color( 1.0f, 0.0f, 0.0f ) );
		statusBar.add( clockLabel );
		statusBar.add( clockDisplay );

		// Create the progress bar
		progressBar = new JProgressBar( 0, 100 );
		progressBar.setValue( 0 );
		progressBar.setStringPainted( true );
		statusBar.add( progressBar );

		// Create a speed-up factor display
		JLabel speedUpLabel = new JLabel( "Speed Up: " );
		speedUpDisplay = new JLabel( "", JLabel.LEFT );
		speedUpDisplay.setPreferredSize( new Dimension( 70, 16 ) );
		speedUpDisplay.setForeground( new Color( 1.0f, 0.0f, 0.0f ) );
		statusBar.add( speedUpLabel );
		statusBar.add( speedUpDisplay );

		// Create a remaining run time display
		JLabel remainingLabel = new JLabel( "Time Remaining: " );
		remainingDisplay = new JLabel( "", JLabel.LEFT );
		remainingDisplay.setPreferredSize( new Dimension( 70, 16 ) );
		remainingDisplay.setForeground( new Color( 1.0f, 0.0f, 0.0f ) );
		statusBar.add( remainingLabel );
		statusBar.add( remainingDisplay );

		// Create a cursor position display
		locatorPos = new JLabel( "", JLabel.LEFT );
		locatorPos.setPreferredSize( new Dimension( 140, 16 ) );
		locatorPos.setForeground( new Color( 1.0f, 0.0f, 0.0f ) );
		locatorLabel = new JLabel( "Position: " );
		statusBar.add( locatorLabel );
		statusBar.add( locatorPos );

		// Add the status bar to the window
		getContentPane().add( statusBar, BorderLayout.SOUTH );
	}

	private long lastSystemTime = System.currentTimeMillis();
	private double lastSimTime = 0.0d;
	private double speedUp = 0.0d;

	/**
	 * Sets the values for the simulation time, run progress, speedup factor,
	 * and remaining run time in the Control Panel's status bar.
	 *
	 * @param simTime - the present simulation time in seconds.
	 */
	public void setClock(double simTime) {

		// Set the simulation time display
		TimeUnit u = (TimeUnit) Unit.getPreferredUnit(TimeUnit.class);
		if (u == null)
			clockDisplay.setText(String.format("%,.2f  %s", simTime, Unit.getSIUnit(TimeUnit.class)));
		else
			clockDisplay.setText(String.format("%,.2f  %s", simTime/u.getConversionFactorToSI(), u.getName()));

		if (getSimState() != SIM_STATE_RUNNING)
			return;

		// Set the run progress bar display
		long cTime = System.currentTimeMillis();
		double duration = Simulation.getRunDuration() + Simulation.getInitializationTime();
		double timeElapsed = simTime - Simulation.getStartHours()*3600.0d;
		int progress = (int)(timeElapsed * 100.0d / duration);
		this.setProgress(progress);

		// Set the speedup factor display
		if (cTime - lastSystemTime > 5000) {
			long elapsedMillis = cTime - lastSystemTime;
			double elapsedSimTime = timeElapsed - lastSimTime;

			// Determine the speed-up factor
			speedUp = (elapsedSimTime * 1000.0d) / elapsedMillis;
			setSpeedUp(speedUp);

			lastSystemTime = cTime;
			lastSimTime = timeElapsed;
		}

		// Set the remaining time display
		if (speedUp > 0.0)
			setRemaining( (duration - timeElapsed)/speedUp );
	}

	/**
	 * Displays the given value on the Control Panel's progress bar.
	 *
	 * @param val - the percent of the run that has completed.
	 */
	public void setProgress( int val ) {
		if (lastValue == val)
			return;

		progressBar.setValue( val );
		progressBar.repaint(25);
		lastValue = val;

		if (getSimState() >= SIM_STATE_CONFIGURED) {
			String title = String.format("%d%% %s - %s", val, Simulation.getModelName(), InputAgent.getRunName());
			setTitle(title);
		}
	}

	/**
	 * Write the given text on the Control Panel's progress bar.
	 *
	 * @param txt - the text to write.
	 */
	public void setProgressText( String txt ) {
		progressBar.setString( txt );
	}

	/**
	 * Write the given value on the Control Panel's speed up factor box.
	 *
	 * @param val - the speed up factor to write.
	 */
	public void setSpeedUp( double val ) {
		speedUpDisplay.setText(String.format("%,.0f", val));
	}

	/**
	 * Write the given value on the Control Panel's remaining run time box.
	 *
	 * @param val - the remaining run time in seconds.
	 */
	public void setRemaining( double val ) {
		if (val > 60.0)
			remainingDisplay.setText(String.format("%.1f  min", val/60.0));
		else
			remainingDisplay.setText(String.format("%.0f  s", val));
	}

	/**
	 * Starts or resumes the simulation run.
	 */
	public void startSimulation() {
		if( getSimState() <= SIM_STATE_CONFIGURED ) {
			if (InputAgent.isSessionEdited()) {
				this.saveAs();
			}
			Simulation.start(currentEvt);
		}
		else if( getSimState() == SIM_STATE_PAUSED ) {
			currentEvt.resume(currentEvt.secondsToNearestTick(Simulation.getPauseTime()));
		}
		else
			throw new ErrorException( "Invalid Simulation State for Start/Resume" );
	}

	/**
	 * Pauses the simulation run.
	 */
	private void pauseSimulation() {
		if( getSimState() == SIM_STATE_RUNNING )
			currentEvt.pause();
		else
			throw new ErrorException( "Invalid Simulation State for pause" );
	}

	/**
	 * Stops the simulation run.
	 */
	public void stopSimulation() {
		if( getSimState() == SIM_STATE_RUNNING ||
		    getSimState() == SIM_STATE_PAUSED ) {
			currentEvt.pause();
			currentEvt.clear();
			this.updateForSimulationState(GUIFrame.SIM_STATE_CONFIGURED);

			// kill all generated objects
			for (int i = 0; i < Entity.getAll().size();) {
				Entity ent = Entity.getAll().get(i);
				if (ent.testFlag(Entity.FLAG_GENERATED))
					ent.kill();
				else
					i++;
			}
		}
		else
			throw new ErrorException( "Invalid Simulation State for stop" );
	}


	/** model was executed, but no configuration performed */
	public static final int SIM_STATE_LOADED = 0;
	/** essential model elements created, no configuration performed */
	public static final int SIM_STATE_UNCONFIGURED = 1;
	/** model has been configured, not started */
	public static final int SIM_STATE_CONFIGURED = 2;
	/** model is presently executing events */
	public static final int SIM_STATE_RUNNING = 3;
	/** model has run, but presently is paused */
	public static final int SIM_STATE_PAUSED = 4;

	private int simState;
	public int getSimState() {
		return simState;
	}

	EventManager currentEvt;
	public void setEventManager(EventManager e) {
		currentEvt = e;
	}

	/**
	 * Sets the state of the simulation run to the given state value.
	 *
	 * @param state - an index that designates the state of the simulation run.
	 */
	public void updateForSimulationState(int state) {
		simState = state;

		switch( getSimState() ) {
			case SIM_STATE_LOADED:
				for( int i = 0; i < fileMenu.getItemCount() - 1; i++ ) {
					fileMenu.getItem(i).setEnabled(true);
				}
				for( int i = 0; i < viewMenu.getItemCount(); i++ ) {
					viewMenu.getItem(i).setEnabled(true);
				}

				windowList.setEnabled( true );
				speedUpDisplay.setEnabled( false );
				remainingDisplay.setEnabled( false );
				controlStartResume.setEnabled( true );
				controlStartResume.setSelected( false );
				controlStartResume.setToolTipText( "Run" );
				controlStop.setEnabled( false );
				controlStop.setSelected( false );
				toolButtonIsometric.setEnabled( true );
				toolButtonXYPlane.setEnabled( true );
				progressBar.setEnabled( false );
				break;

			case SIM_STATE_UNCONFIGURED:
				for( int i = 0; i < fileMenu.getItemCount() - 1; i++ ) {
					fileMenu.getItem(i).setEnabled(true);
				}
				for( int i = 0; i < viewMenu.getItemCount(); i++ ) {
					viewMenu.getItem(i).setEnabled(true);
				}

				windowList.setEnabled( true );
				speedUpDisplay.setEnabled( false );
				remainingDisplay.setEnabled( false );
				controlStartResume.setEnabled( false );
				controlStartResume.setSelected( false );
				controlStop.setSelected( false );
				controlStop.setEnabled( false );
				toolButtonIsometric.setEnabled( true );
				toolButtonXYPlane.setEnabled( true );
				progressBar.setEnabled( false );
				showPosition.setState( true );
				setShowPositionXY();
				break;

			case SIM_STATE_CONFIGURED:
				for( int i = 0; i < fileMenu.getItemCount() - 1; i++ ) {
					fileMenu.getItem(i).setEnabled(true);
				}
				for( int i = 0; i < viewMenu.getItemCount(); i++ ) {
					viewMenu.getItem(i).setEnabled(true);
				}

				windowList.setEnabled( true );
				speedUpDisplay.setEnabled( false );
				remainingDisplay.setEnabled( false );
				controlStartResume.setEnabled( true );
				controlStartResume.setSelected( false );
				controlStartResume.setToolTipText( "Run" );
				controlStop.setSelected( false );
				controlStop.setEnabled( false );
				toolButtonIsometric.setEnabled( true );
				toolButtonXYPlane.setEnabled( true );
				progressBar.setEnabled( true );
				break;

			case SIM_STATE_RUNNING:
				speedUpDisplay.setEnabled( true );
				remainingDisplay.setEnabled( true );
				controlStartResume.setEnabled( true );
				controlStartResume.setSelected( true );
				controlStartResume.setToolTipText( "Pause" );
				controlStop.setEnabled( true );
				controlStop.setSelected( false );
				break;

			case SIM_STATE_PAUSED:
				controlStartResume.setEnabled( true );
				controlStartResume.setSelected( false );
				controlStartResume.setToolTipText( "Run" );
				controlStop.setEnabled( true );
				controlStop.setSelected( false );
				break;
			default:
				throw new ErrorException( "Unrecognized Graphics State" );
		}
		fileMenu.setEnabled( true );
	}

	/**
	 * updates RealTime button and Spinner
	 */
	public void updateForRealTime(boolean executeRT, int factorRT) {
		currentEvt.setExecuteRealTime(executeRT, factorRT);
		controlRealTime.setSelected(executeRT);
		spinner.setValue(factorRT);
	}

	/**
	 * updates PauseTime entry
	 */
	public void updateForPauseTime(String str) {
		pauseTime.setText(str);
	}

	/**
	 * Sets the PauseTime keyword for Simulation.
	 * @param str - value to assign.
	 */
	private void setPauseTime(String str) {
		Input<?> pause = Simulation.getInstance().getInput("PauseTime");
		String prevVal = pause.getValueString();
		if (prevVal.equals(str))
			return;

		ArrayList<String> tokens = new ArrayList<>();
		Parser.tokenize(tokens, str);
		// if we only got one token, and it isn't RFC8601 - add a unit
		if (tokens.size() == 1 && !tokens.get(0).contains(" ")) {
			Unit u = Unit.getPreferredUnit(TimeUnit.class);
			if (u == null)
				tokens.add(Unit.getSIUnit(TimeUnit.class));
			else
				tokens.add(u.getName());
		}

		try {
			// Parse the keyword inputs
			KeywordIndex kw = new KeywordIndex("PauseTime", tokens, null);
			InputAgent.apply(Simulation.getInstance(), kw);
		}
		catch (InputErrorException e) {
			pauseTime.setText(prevVal);
			GUIFrame.showErrorDialog("Input Error", e.getMessage());
		}
	}

	public static Image getWindowIcon() {
		return iconImage;
	}

	public void copyLocationToClipBoard(Vec3d pos) {
		String data = String.format("(%.3f, %.3f, %.3f)", pos.x, pos.y, pos.z);
		StringSelection stringSelection = new StringSelection(data);
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents( stringSelection, null );
	}

	public void showLocatorPosition(Vec3d pos) {
		// null indicates nothing to display
		if( pos == null ) {
			locatorPos.setText( "-------" );
		}
		else {
			if( showPosition.getState() ) {
				DistanceUnit u = (DistanceUnit) Unit.getPreferredUnit(DistanceUnit.class);
				if (u == null) {
					locatorPos.setText(String.format((Locale)null, "%.3f  %.3f  %.3f  %s",
							pos.x, pos.y, pos.z, Unit.getSIUnit(DistanceUnit.class)));
				}
				else {
					double factor = u.getConversionFactorToSI();
					locatorPos.setText(String.format((Locale)null, "%.3f  %.3f  %.3f  %s",
							pos.x/factor, pos.y/factor, pos.z/factor, u.getName()));
				}
			}
		}
	}

	public void setShowPositionXY() {
		boolean show = showPosition.getState();
		showPosition.setState( show );
		locatorLabel.setVisible( show );
		locatorPos.setVisible( show );
		locatorLabel.setText( "Position:  " );
		locatorPos.setText( "------" );
	}

	public void enableSave(boolean bool) {
		saveConfigurationMenuItem.setEnabled(bool);
	}

	/**
	 * Sets variables used to determine the position and size of various
	 * windows based on the size of the computer display being used.
	 */
	private static void calcWindowDefaults() {
		Dimension guiSize = GUIFrame.instance().getSize();
		Rectangle winSize = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

		COL1_WIDTH = 220;
		COL2_WIDTH = Math.min(520, (winSize.width - COL1_WIDTH) / 2);
		COL3_WIDTH = Math.min(420, winSize.width - COL1_WIDTH - COL2_WIDTH);

		COL1_START = 0;
		COL2_START = COL1_START + COL1_WIDTH;
		COL3_START = COL2_START + COL2_WIDTH;

		HALF_TOP = (winSize.height - guiSize.height) / 2;
		HALF_BOTTOM = (winSize.height - guiSize.height - HALF_TOP);

		TOP_START = guiSize.height;
		BOTTOM_START = TOP_START + HALF_TOP;

		LOWER_HEIGHT = (winSize.height - guiSize.height) / 3;
		LOWER_START = winSize.height - LOWER_HEIGHT;

		VIEW_WIDTH = COL2_WIDTH + COL3_WIDTH;
		VIEW_HEIGHT = winSize.height - TOP_START - LOWER_HEIGHT;
	}

	/**
	 * Displays the view windows and tools on startup.
	 *
	 * @param viewOnly - boolean that determines whether the tools are to be displayed.
	 *                   TRUE  - show just the view windows.
	 *                   FALSE - show the view windows and the tools.
	 */
	public static void displayWindows() {

		// Show the view windows specified in the configuration file
		for (View v : View.getAll()) {
			if (v.showWindow())
				RenderManager.inst().createWindow(v);
		}

		// Set the selected entity to the first view window
		if (View.getAll().size() > 0)
			FrameBox.setSelectedEntity(View.getAll().get(0));
	}

	// ******************************************************************************************************
	// MAIN
	// ******************************************************************************************************

	public static void main( String args[] ) {
		// Process the input arguments and filter out directives
		ArrayList<String> configFiles = new ArrayList<>(args.length);
		boolean batch = false;
		boolean minimize = false;
		boolean quiet = false;

		for (String each : args) {
			// Batch mode
			if (each.equalsIgnoreCase("-b") ||
			    each.equalsIgnoreCase("-batch")) {
				batch = true;
				continue;
			}
			// z-buffer offset
			if (each.equalsIgnoreCase("-z") ||
			    each.equalsIgnoreCase("-zbuffer")) {
				// Parse the option, but do nothing
				continue;
			}
			// Minimize model window
			if (each.equalsIgnoreCase("-m") ||
			    each.equalsIgnoreCase("-minimize")) {
				minimize = true;
				continue;
			}
			// Do not open default windows
			if (each.equalsIgnoreCase("-q") ||
					each.equalsIgnoreCase("-quiet")) {
				quiet = true;
				continue;
			}
			if (each.equalsIgnoreCase("-sg") ||
					each.equalsIgnoreCase("-safe_graphics")) {
				SAFE_GRAPHICS = true;
				continue;
			}
			// Not a program directive, add to list of config files
			configFiles.add(each);
		}

		// If not running in batch mode, create the splash screen
		JWindow splashScreen = null;
		if (!batch) {
			URL splashImage = GUIFrame.class.getResource("/resources/images/splashscreen.png");
			ImageIcon imageIcon = new ImageIcon(splashImage);
			Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
			int splashX = (screen.width - imageIcon.getIconWidth()) / 2;
			int splashY = (screen.height - imageIcon.getIconHeight()) / 2;

			// Set the window's bounds, centering the window
			splashScreen = new JWindow();
			splashScreen.setAlwaysOnTop(true);
			splashScreen.setBounds(splashX, splashY, imageIcon.getIconWidth(), imageIcon.getIconHeight());

			// Build the splash screen
			splashScreen.getContentPane().add(new JLabel(imageIcon));

			// Display it
			splashScreen.setVisible(true);

			// Begin initializing the rendering system
			RenderManager.initialize(SAFE_GRAPHICS);
		}

		// create a graphic simulation
		LogBox.logLine("Loading Simulation Environment ... ");

		EventManager evt = new EventManager("DefaultEventManager");
		GUIFrame gui = GUIFrame.instance();
		gui.setEventManager(evt);
		gui.updateForSimulationState(SIM_STATE_LOADED);
		evt.setTimeListener(gui);
		evt.setErrorListener(gui);

		LogBox.logLine("Simulation Environment Loaded");

		if (batch)
			InputAgent.setBatch(true);

		if (minimize)
			gui.setExtendedState(JFrame.ICONIFIED);

		// Show the Control Panel
		gui.setVisible(true);
		GUIFrame.calcWindowDefaults();

		// Load the autoload file
		InputAgent.setRecordEdits(false);
		InputAgent.readResource("inputs/autoload.cfg");
		gui.setTitle(Simulation.getModelName());

		// Resolve all input arguments against the current working directory
		File user = new File(System.getProperty("user.dir"));
		// Process any configuration files passed on command line
		// (Multiple configuration files are not supported at present)
		for (int i = 0; i < configFiles.size(); i++) {
			//InputAgent.configure(gui, new File(configFiles.get(i)));
			File abs = new File((File)null, configFiles.get(i));
			File loadFile;
			if (abs.exists())
				loadFile = abs.getAbsoluteFile();
			else
				loadFile = new File(user, configFiles.get(i));

			Throwable t = gui.configure(loadFile);
			if (t != null) {
				// Hide the splash screen
				if (splashScreen != null) {
					splashScreen.dispose();
					splashScreen = null;
				}
				handleConfigError(t, loadFile);
			}
		}

		// If no configuration files were specified on the command line, then load the default configuration file
		if( configFiles.size() == 0 ) {
			InputAgent.setRecordEdits(true);
			InputAgent.loadDefault();
			gui.updateForSimulationState(GUIFrame.SIM_STATE_CONFIGURED);
		}

		// Show the view windows
		if(!quiet && !batch) {
			displayWindows();
		}

		// If in batch or quiet mode, close the any tools that were opened
		if (quiet || batch)
			Simulation.closeAllTools();

		// Set RecordEdits mode (if it has not already been set in the configuration file)
		InputAgent.setRecordEdits(true);

		// Start the model if in batch mode
		if (batch) {
			if (InputAgent.numErrors() > 0)
				GUIFrame.shutdown(0);
			Simulation.start(evt);
			return;
		}

		// Wait to allow the renderer time to finish initialisation
		try { Thread.sleep(1000); } catch (InterruptedException e) {}

		// Hide the splash screen
		if (splashScreen != null) {
			splashScreen.dispose();
			splashScreen = null;
		}

		// Bring the Control Panel to the front (along with any open Tools)
		gui.toFront();
	}

	public static class SpeedFactorListener implements ChangeListener {

		@Override
		public void stateChanged( ChangeEvent e ) {
			ArrayList<String> arg = new ArrayList<>(1);
			arg.add(String.format("%d", ((JSpinner)e.getSource()).getValue()));
			InputAgent.apply(Simulation.getInstance(), new KeywordIndex("RealTimeFactor", arg, null));
		}
	}

	/*
	 * this class is created so the next value will be value * 2 and the
	 * previous value will be value / 2
	 */
	public static class SpinnerModel extends SpinnerNumberModel {
		private int value;
		public SpinnerModel( int val, int min, int max, int stepSize) {
			super(val, min, max, stepSize);
		}

		@Override
		public Object getPreviousValue() {
			value = this.getNumber().intValue() / 2;

			// Avoid going beyond limit
			Integer min = (Integer)this.getMinimum();
			if (min.intValue() > value) {
				return min;
			}
			return value;
		}

		@Override
		public Object getNextValue() {
			value = this.getNumber().intValue() * 2;

			// Avoid going beyond limit
			Integer max = (Integer)this.getMaximum();
			if (max.intValue() < value) {
				return max;
			}
			return value;
		}
	}

	public static class RealTimeActionListener implements ActionListener {
		@Override
		public void actionPerformed( ActionEvent event ) {
			ArrayList<String> arg = new ArrayList<>(1);
			if (((JToggleButton)event.getSource()).isSelected())
				arg.add("TRUE");
			else
				arg.add("FALSE");
			InputAgent.apply(Simulation.getInstance(), new KeywordIndex("RealTime", arg, null));
		}
	}

	public static boolean getShuttingDownFlag() {
		return shuttingDown.get();
	}

	public static void shutdown(int errorCode) {

		shuttingDown.set(true);
		if (RenderManager.isGood()) {
			RenderManager.inst().shutdown();
		}

		System.exit(errorCode);
	}

	@Override
	public void tickUpdate(long tick) {
		FrameBox.timeUpdate(tick);
	}

	@Override
	public void timeRunning(boolean running) {
		if (running) {
			updateForSimulationState(SIM_STATE_RUNNING);
		}
		else {
			updateForSimulationState(SIM_STATE_PAUSED);
		}
	}

	@Override
	public void handleError(EventManager evt, Throwable t, long currentTick) {
		if (t instanceof OutOfMemoryError) {
			OutOfMemoryError e = (OutOfMemoryError)t;
			LogBox.logLine("Out of Memory use the -Xmx flag during execution for more memory");
			LogBox.logLine("Further debug information:");
			LogBox.logLine("Error: " + e.getMessage());
			for (StackTraceElement each : e.getStackTrace())
				LogBox.logLine(each.toString());
			GUIFrame.shutdown(1);
			return;
		}
		else {
			double curSec = evt.ticksToSeconds(currentTick);
			LogBox.format("EXCEPTION AT TIME: %f s%n", curSec);
			LogBox.logLine("Error: " + t.getMessage());
			for (StackTraceElement each : t.getStackTrace())
				LogBox.logLine(each.toString());
		}

		GUIFrame.showErrorDialog("Runtime Error",
		                         "JaamSim has detected the following runtime error condition:\n\n%s\n\n" +
		                         "Programmers can find more information by opening the Log Viewer.\n" +
		                         "The simulation run must be stopped before it can be restarted.",
		                         t.getMessage());
	}

	/**
	 * Shows the Error Message dialog box
	 * @param title - text for the dialog box name
	 * @param fmt - format string for the error message
	 * @param args - inputs to the error message
	 */
	public static void showErrorDialog(String title, String fmt, Object... args) {
		if (InputAgent.getBatch()) GUIFrame.shutdown(1);

		final String msg = String.format(fmt,  args);
		JOptionPane.showMessageDialog(null, msg, title, JOptionPane.ERROR_MESSAGE);
	}

	void load() {
		LogBox.logLine("Loading...");

		// Create a file chooser
		final JFileChooser chooser = new JFileChooser(InputAgent.getConfigFile());

		// Set the file extension filters
		chooser.setAcceptAllFileFilterUsed(true);
		FileNameExtensionFilter cfgFilter =
				new FileNameExtensionFilter("JaamSim Configuration File (*.cfg)", "CFG");
		chooser.addChoosableFileFilter(cfgFilter);
		chooser.setFileFilter(cfgFilter);

		// Show the file chooser and wait for selection
		int returnVal = chooser.showOpenDialog(this);

		// Load the selected file
		if (returnVal == JFileChooser.APPROVE_OPTION) {
            File temp = chooser.getSelectedFile();
			final GUIFrame gui1 = this;
    		final File chosenfile = temp;
			new Thread(new Runnable() {
				@Override
				public void run() {
					InputAgent.setRecordEdits(false);
					gui1.clear();
					Throwable ret = gui1.configure(chosenfile);
					if (ret != null)
						handleConfigError(ret, chosenfile);

					InputAgent.setRecordEdits(true);

					GUIFrame.displayWindows();
					FrameBox.valueUpdate();
				}
			}).start();
        }
	}

	Throwable configure(File file) {
		InputAgent.setConfigFile(file);
		this.updateForSimulationState(GUIFrame.SIM_STATE_UNCONFIGURED);

		Throwable ret = null;
		try {
			InputAgent.loadConfigurationFile(file);
		}
		catch (Throwable t) {
			ret = t;
		}

		if (ret == null)
			LogBox.logLine("Configuration File Loaded");
		else
			LogBox.logLine("Configuration File Loaded - errors found");

		// show the present state in the user interface
		this.setProgressText(null);
		this.setProgress(0);
		this.setTitle( Simulation.getModelName() + " - " + InputAgent.getRunName() );
		this.updateForSimulationState(GUIFrame.SIM_STATE_CONFIGURED);
		this.enableSave(InputAgent.getRecordEditsFound());

		return ret;
	}

	static void handleConfigError(Throwable t, File file) {
		if (t instanceof InputErrorException) {
			LogBox.logLine("Input Error: " + t.getMessage());
			GUIFrame.showErrorDialog("Input Error",
			                         "Input errors were detected while loading file: '%s'\n\n%s\n\n" +
			                         "Check the log file '%s' for more information.",
			                         file.getName(), t.getMessage(), InputAgent.getRunName() + ".log");
			return;
		}

		LogBox.format("Fatal Error while loading file '%s': %s\n", file.getName(), t.getMessage());
		GUIFrame.showErrorDialog("Fatal Error",
		                         "A fatal error has occured while loading the file '%s':\n\n%s",
		                         file.getName(), t.getMessage());
	}

	/**
	 * Saves the configuration file.
	 * @param gui = Control Panel window for JaamSim
	 * @param fileName = absolute file path and file name for the file to be saved
	 */
	private void setSaveFile(String fileName) {

		// Set root directory
		File temp = new File(fileName);

		// Save the configuration file
		InputAgent.printNewConfigurationFileWithName( fileName );
		InputAgent.setConfigFile(temp);

		// Set the title bar to match the new run name
		this.setTitle( Simulation.getModelName() + " - " + InputAgent.getRunName() );
	}

	void save() {
		LogBox.logLine("Saving...");
		if( InputAgent.getConfigFile() != null ) {
			setSaveFile(InputAgent.getConfigFile().getPath());
		}
		else {
			saveAs();
		}
	}

	void saveAs() {
		LogBox.logLine("Save As...");

		// Create a file chooser
		final JFileChooser chooser = new JFileChooser(InputAgent.getConfigFile());

		// Set the file extension filters
		chooser.setAcceptAllFileFilterUsed(true);
		FileNameExtensionFilter cfgFilter =
				new FileNameExtensionFilter("JaamSim Configuration File (*.cfg)", "CFG");
		chooser.addChoosableFileFilter(cfgFilter);
		chooser.setFileFilter(cfgFilter);
		chooser.setSelectedFile(InputAgent.getConfigFile());

		// Show the file chooser and wait for selection
		int returnVal = chooser.showSaveDialog(this);

		// Load the selected file
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			String filePath = file.getPath();

			// Add the file extension ".cfg" if needed
			filePath = filePath.trim();
			if (filePath.indexOf(".") == -1)
				filePath = filePath.concat(".cfg");

			// Confirm overwrite if file already exists
			File temp = new File(filePath);
			if (temp.exists()) {
				boolean confirmed = GUIFrame.showSaveAsDialog(file.getName());
				if (!confirmed) {
					return;
				}
			}

			// Save the configuration file
			setSaveFile(filePath);
		}
	}

	/**
	 * Shows the "Confirm Save As" dialog box
	 * @param fileName - name of the file to be saved
	 * @return true if the file is to be overwritten.
	 */
	public static boolean showSaveAsDialog(String fileName) {
		int userOption = JOptionPane.showConfirmDialog(null,
				String.format("The file '%s' already exists.\n" +
						"Do you want to replace it?", fileName),
				"Confirm Save As",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
		return (userOption == JOptionPane.YES_OPTION);
	}

	/**
	 * Shows the "Confirm Stop" dialog box.
	 * @return true if the run is to be stopped.
	 */
	public static boolean showConfirmStopDialog() {
		int userOption = JOptionPane.showConfirmDialog( null,
				"WARNING: If you stop the run, it can only be re-started from time 0.\n" +
				"Do you really want to stop?",
				"Confirm Stop",
				JOptionPane.YES_OPTION,
				JOptionPane.WARNING_MESSAGE );
		return (userOption == JOptionPane.YES_OPTION);
	}

	/**
	 * Shows the "Save Changes" dialog box
	 * @return true for any response other than Cancel.
	 */
	public static boolean showSaveChangesDialog() {

		String message;
		if (InputAgent.getConfigFile() == null)
			message = "Do you want to save the changes you made?";
		else
			message = String.format("Do you want to save the changes you made to '%s'?", InputAgent.getConfigFile().getName());

		Object[] options = {"Save", "Don't Save", "Cancel"};
		int userOption = JOptionPane.showOptionDialog( null,
				message,
				"Save Changes",
				JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE,
				null,
				options,
				options[0]);

		if (userOption == JOptionPane.YES_OPTION)
			GUIFrame.instance().save();

		return (userOption != JOptionPane.CANCEL_OPTION);
	}

}