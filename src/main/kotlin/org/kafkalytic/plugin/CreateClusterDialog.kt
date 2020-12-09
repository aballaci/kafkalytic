package org.kafkalytic.plugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.ui.table.JBTable
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.security.plain.PlainLoginModule
import java.awt.*
import java.io.FileReader
import java.io.IOException
import java.util.*
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.table.DefaultTableModel


class CreateClusterDialog(val project: Project) : Messages.InputDialog(
        "Enter Kafka bootstrap servers as host:port,host2:port",
        "New cluster",
        Messages.getQuestionIcon(),
        null,
        object: InputValidator {
            //host:port,
            private val matcher = """([a-zA-Z0-9\.\-_]+:[0-9]{1,5},)*([a-zA-Z0-9-_]+\.)*([a-zA-Z0-9-_])+:[0-9]{1,5}""".toRegex()
            override fun checkInput(inputString: String?) = inputString != null && matcher.matches(inputString)
            override fun canClose(inputString: String?) = checkInput(inputString)
        }) {

    private val LOG = Logger.getInstance(this::class.java)
    private lateinit var name: JTextField
    private lateinit var tableModel: DefaultTableModel
    private lateinit var trustPath: JTextField
    private lateinit var keyPath: JTextField
    private lateinit var trustPassword: JTextField
    private lateinit var keyPassword: JTextField
    private lateinit var requestTimeout: JTextField
    private lateinit var jaasConfig: JTextField
    private lateinit var saslMechanism: JTextField
    private lateinit var securityProtocol: JTextField
    private lateinit var plainLoginModule: PlainLoginModule


    override fun createMessagePanel(): JPanel {
        val messagePanel = JPanel(BorderLayout())
        if (myMessage != null) {
            val textComponent = createTextComponent()
            messagePanel.add(textComponent, BorderLayout.NORTH)
        }

        plainLoginModule  = PlainLoginModule()
        tableModel = DefaultTableModel()
        tableModel.addColumn("Property")
        tableModel.addColumn("Value")
        tableModel.addTableModelListener {  }
        myField = HintTextField("host1:port,host2:port")
        messagePanel.add(createScrollableTextComponent(), BorderLayout.CENTER)
        val browse = JButton("Load properties from file")
        val testConnection = JButton("Test connection")
        testConnection.addActionListener {
            try {
                val current = Thread.currentThread().contextClassLoader
                Thread.currentThread().setContextClassLoader(current)
                AdminClient.create(getCluster() as Map<String, Any>).close()
                info("Connection successful")
            } catch (e: KafkaException) {
                info("Cannot connect to Kafka cluster. $e")
            }
        }
        browse.addActionListener {
            val fcd = FileChooserDescriptor(true, false, false, false, false, false)
            val props = Properties()
            val file = FileChooser.chooseFile(fcd, project, null)
            if (file != null) {
                props.load(FileReader(file.canonicalPath))
                props.entries.forEach { tableModel.addRow(arrayOf(it.key, it.value)) }
                props.getProperty("bootstrap.servers")?.let { myField.text = it }
                props.getProperty("ssl.truststore.location")?.let { trustPath.text = it }
                props.getProperty("ssl.truststore.password")?.let { trustPassword.text = it }
                props.getProperty("ssl.keystore.location")?.let { keyPath.text = it }
                props.getProperty("ssl.keystore.password")?.let { keyPassword.text = it }
            }
        }
        name = JTextField("dev")
        val subPanel = JPanel(BorderLayout())
        subPanel.add(layoutLR(JLabel("Cluster name (optional)"), name), BorderLayout.NORTH)
        val certSubPanel = JPanel(GridLayout(0, 2))
        trustPath = HintTextField("local path")
        keyPath = HintTextField("local path")
        trustPassword = JTextField()
        keyPassword = JTextField()
        requestTimeout = JTextField("5000")
        jaasConfig = JTextField("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"user\" password=\"password\";")
        saslMechanism = JTextField("PLAIN")
        securityProtocol = JTextField("SASL_PLAINTEXT")
        requestTimeout.inputVerifier = INT_VERIFIER
        certSubPanel.addLabelled("Truststore path", trustPath)
        certSubPanel.addLabelled("Truststore password", trustPassword)
        certSubPanel.addLabelled("Keystore path", keyPath)
        certSubPanel.addLabelled("Keystore password", keyPassword)
        certSubPanel.addLabelled("Request timeout, ms", requestTimeout)
        certSubPanel.addLabelled("Jaas Config", jaasConfig)
        certSubPanel.addLabelled("SASL Mechanism", saslMechanism)
        certSubPanel.addLabelled("Security Protocol", securityProtocol)
        subPanel.add(certSubPanel, BorderLayout.CENTER)
        subPanel.add(layoutUD(browse, JBTable(tableModel), testConnection), BorderLayout.SOUTH)

        messagePanel.add(subPanel, BorderLayout.SOUTH)
        return messagePanel
    }

    fun getCluster(): MutableMap<String, String> {
        var props = tableModel.dataVector.elements().asSequence()
                .map { val v = it as Vector<*>; v[0].toString() to v[1].toString() }.toMap().toMutableMap()
        if (!myField.text.trim().isNullOrEmpty()) {
            props.put("bootstrap.servers", myField.text.trim())
        }
        props.put("name", name.text.ifBlank { props["bootstrap.servers"]!! })
        if (requestTimeout.text.isNotBlank()) {
            props.put("request.timeout.ms", requestTimeout.text)
        }
        if (jaasConfig.text.isNotBlank()) {
//            props.put("sasl.jaas.config", jaasConfig.text)
            props.put("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"user\" password=\"password\";");
            props.put(SaslConfigs.SASL_MECHANISM, saslMechanism.text)
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol.text)
//            props.put("sasl.client.callback.handler.class", "org.apache.kafka.common.security.plain.PlainAuthenticateCallback")
        }
        if (trustPath.text.isNotBlank()) {
            props.putAll(mapOf(
                    "security.protocol" to "SSL",
                    "ssl.truststore.location" to trustPath.text,
                    "ssl.truststore.password" to trustPassword.text,
                    "ssl.keystore.location" to keyPath.text,
                    "ssl.keystore.password" to keyPassword.text))
        }

        LOG.info("coonection properties:$props")
        return props
    }
}


