package com.vinnyvangogh.questpath.scenarios;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.ui.PluginPanel;

/**
 * The Scenarios sidebar. <b>Scenarios</b>: pick a built-in or saved scenario, Run it (steps keep their waits) or
 * Step through it one event at a time. <b>Paste JSON</b>: paste an event, a list of events or a whole scenario and
 * Send it now, or Save it under a name. Swing thread only; events leave through {@code post}, which hands them to
 * the client thread.
 */
final class ScenarioPanel extends PluginPanel
{
	private static final String ALL = "All plugins";
	private static final String WARNING = "Outputs are real: a webhook you set gets real posts, so point it at a "
		+ "test channel first (or turn on Dry run, for outputs wired to it).";

	private final EventKinds kinds;
	private final ScenarioStore store;
	private final Consumer<Object> everyone;
	private final Consumer<Object> theOwner;
	private final String ownerLabel;
	private final JComboBox<String> target = new JComboBox<>();
	private final JCheckBox dryRun = new JCheckBox("Dry run outputs");
	private final JComboBox<Scenario> picker = new JComboBox<>();
	private final JTextArea about = text(3, false);
	private final JTextArea paste = text(10, true);
	private final JTextArea log = text(12, false);
	private ScenarioRun run;
	private Timer timer;

	/**
	 * {@code theOwner} (and its label) may be null when the launcher did not say which plugin is under test; then
	 * only "All plugins" is offered. Otherwise "This plugin only" is the default.
	 */
	ScenarioPanel(EventKinds kinds, ScenarioStore store, Consumer<Object> everyone, Consumer<Object> theOwner,
		String ownerLabel)
	{
		this.kinds = kinds;
		this.store = store;
		this.everyone = everyone;
		this.theOwner = theOwner;
		this.ownerLabel = ownerLabel;
		setLayout(new BorderLayout(0, 6));

		if (theOwner != null)
		{
			target.addItem(ownerOnly());
		}
		target.addItem(ALL);
		target.setToolTipText("This plugin only: a private event bus with just the plugin under test on it. "
			+ "All plugins: the client's real bus, for cross-plugin tests.");
		dryRun.setToolTipText("Off by default. On: outputs wired to DryRun write to the log instead of sending.");
		dryRun.addActionListener(e -> DryRun.shared().enabled(dryRun.isSelected()));
		DryRun.shared().log(line -> SwingUtilities.invokeLater(() -> say(line)));
		picker.addActionListener(e -> chosen());

		JPanel top = new JPanel(new BorderLayout(0, 4));
		JTextArea warning = text(4, false);
		warning.setText(WARNING);
		top.add(warning, BorderLayout.NORTH);
		top.add(labelled("Send to:", target), BorderLayout.CENTER);
		top.add(dryRun, BorderLayout.SOUTH);

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Scenarios", scenariosTab());
		tabs.addTab("Paste JSON", pasteTab());

		add(top, BorderLayout.NORTH);
		add(tabs, BorderLayout.CENTER);
		add(new JScrollPane(log), BorderLayout.SOUTH);
	}

	private String ownerOnly()
	{
		return ownerLabel + " only";
	}

	/** The bus the next event goes to, read at fire time so changing the picker mid-run takes effect. */
	private void post(Object event)
	{
		boolean one = theOwner != null && ownerOnly().equals(target.getSelectedItem());
		(one ? theOwner : everyone).accept(event);
	}

	/** Writes a line to the log from any thread. */
	void report(String line)
	{
		SwingUtilities.invokeLater(() -> say(line));
	}

	private static JPanel labelled(String label, JComponent field)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.add(new JLabel(label), BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);
		return row;
	}

	/**
	 * Two columns, two rows: at the sidebar's 225 px each button gets about 100 px, enough for these words. The
	 * margin is trimmed because a Swing button elides its label ("St...") when its text plus insets do not fit.
	 */
	private JPanel scenariosTab()
	{
		JPanel buttons = new JPanel(new GridLayout(2, 2, 4, 4));
		buttons.add(button("Run", "Play every step with its waits", this::runAll));
		buttons.add(button("Step", "Fire the next step only", this::stepOnce));
		buttons.add(button("Reset", "Stop and start the scenario over", this::reset));
		buttons.add(button("Reload", "Read the scenario files again", this::reload));

		JPanel tab = new JPanel(new BorderLayout(0, 6));
		tab.add(picker, BorderLayout.NORTH);
		tab.add(about, BorderLayout.CENTER);
		tab.add(buttons, BorderLayout.SOUTH);
		return tab;
	}

	private JPanel pasteTab()
	{
		JPanel buttons = new JPanel(new GridLayout(2, 2, 4, 4));
		buttons.add(button("Send", "Fire it now, waits ignored", this::sendPasted));
		buttons.add(button("Save", "Keep it as a named scenario", this::savePasted));
		buttons.add(button("Kinds", "List every kind with a working example", this::showKinds));
		buttons.add(button("Clear", "Empty the text box", () -> paste.setText("")));

		JPanel tab = new JPanel(new BorderLayout(0, 6));
		tab.add(new JScrollPane(paste), BorderLayout.CENTER);
		tab.add(buttons, BorderLayout.SOUTH);
		return tab;
	}

	private static JTextArea text(int rows, boolean editable)
	{
		JTextArea area = new JTextArea(rows, 20);
		area.setEditable(editable);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		return area;
	}

	private static JButton button(String label, String tooltip, Runnable action)
	{
		JButton b = new JButton(label);
		b.setMargin(new Insets(2, 2, 2, 2));
		b.setToolTipText(tooltip);
		b.addActionListener(e -> action.run());
		return b;
	}

	/** Reads every scenario again: built-ins from the test resources, then the ones saved in the dev folder. */
	void reload()
	{
		reset();
		picker.removeAllItems();
		List<String> problems = new ArrayList<>();
		try
		{
			for (Scenario s : ScenarioParser.loadAll(ScenarioParser.DEFAULT_DIR, kinds))
			{
				picker.addItem(s);
			}
		}
		catch (ScenarioException e)
		{
			problems.add(e.getMessage());
		}
		try
		{
			for (Scenario s : store.list(problems))
			{
				picker.addItem(s);
			}
		}
		catch (IOException e)
		{
			problems.add("cannot read saved scenarios: " + e.getMessage());
		}
		say("Loaded " + picker.getItemCount() + " scenarios.");
		problems.forEach(this::say);
	}

	private void chosen()
	{
		reset();
		Scenario s = (Scenario) picker.getSelectedItem();
		about.setText(s == null ? "" : s.description == null ? s.name : s.description);
	}

	private ScenarioRun current()
	{
		Scenario s = (Scenario) picker.getSelectedItem();
		if (s != null && (run == null || !run.hasNext()))
		{
			run = new ScenarioRun(s, kinds, this::post).live(true);
			say("- " + s.name + " -");
		}
		return run;
	}

	private void stepOnce()
	{
		stopTimer();
		fire(current());
	}

	private void runAll()
	{
		stopTimer();
		ScenarioRun r = current();
		if (r != null)
		{
			schedule(r);
		}
	}

	/** The next step after its wait, then the one after that, until the scenario ends or Reset. */
	private void schedule(ScenarioRun r)
	{
		if (!r.hasNext())
		{
			say("Done. Check the plugin's panel, overlay and outputs.");
			return;
		}
		timer = new Timer((int) Math.min(Integer.MAX_VALUE, r.nextWait()), e ->
		{
			fire(r);
			schedule(r);
		});
		timer.setRepeats(false);
		timer.start();
	}

	private void fire(ScenarioRun r)
	{
		if (r == null || !r.hasNext())
		{
			return;
		}
		try
		{
			say(r.step().toString());
		}
		catch (ScenarioException e)
		{
			say(e.getMessage());
		}
	}

	/** Parses the pasted text, then fires every step at once: a paste says "now", so waits are ignored. */
	private void sendPasted()
	{
		try
		{
			Scenario s = ScenarioParser.parsePasted(paste.getText(), kinds);
			ScenarioRun r = new ScenarioRun(s, kinds, this::post).live(true);
			say("- " + s.name + " (pasted) -");
			while (r.hasNext())
			{
				fire(r);
			}
		}
		catch (ScenarioException e)
		{
			say(e.getMessage());
		}
	}

	private void savePasted()
	{
		String name = JOptionPane.showInputDialog(this, "Name for this scenario:");
		if (name == null)
		{
			return;
		}
		try
		{
			say("Saved " + store.save(name, paste.getText()));
			reload();
		}
		catch (ScenarioException | IOException e)
		{
			say(e.getMessage());
		}
	}

	private void showKinds()
	{
		say("Kinds (see SCENARIOS.md):");
		for (EventKinds.Doc d : kinds.docs())
		{
			say(d.kind + ": " + d.fields + "\n  " + d.example);
		}
	}

	private void reset()
	{
		stopTimer();
		run = null;
		log.setText("");
	}

	private void stopTimer()
	{
		if (timer != null)
		{
			timer.stop();
			timer = null;
		}
	}

	private void say(String line)
	{
		log.append(line + "\n");
	}
}
