package com.example.mtz_5555_transp.bluetoothapplication;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class BluetoothChatActivity extends AppCompatActivity implements DialogInterface.OnClickListener, OnCancelListener {

    private static final String SERVICO = "DominandoChat";
    private static final UUID MEU_UUID =
            UUID.fromString("523ac59f-e406-46cf-8e7c-4bfa573cdf93");
    private static final int BT_TEMPO_DESCOBERTA = 30;
    private static final int BT_ATIVAR = 0;
    private static final int BT_VISIVEL = 1;
    private static final int MSG_TEXTO = 0;
    private static final int MSG_DESCONECTOU = 2;

    private ThreadServidor mThreadServidor;
    private ThreadCliente mThreadCliente;
    private ThreadComunicacao mThreadComunicacao;

    private BluetoothAdapter mBluetoothAdapter;
    private List<BluetoothDevice> mDispositivosEncontrados;
    private EventosBluetoothReceiver mEventosBluetoothReceiver;
    private DataInputStream is;
    private DataOutputStream os;

    private ArrayAdapter<String> mMensagens;
    private TelaHandler mTelaHandler;
    private ProgressDialog mAguardeDialog;

    @BindView(R.id.lstHistorico)
    ListView mListView;

    @BindView(R.id.edtMsg)
    EditText mEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_chat);
        ButterKnife.bind(this);

        mTelaHandler = new TelaHandler();
        mMensagens = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        mListView.setAdapter(mMensagens);

        mEventosBluetoothReceiver = new EventosBluetoothReceiver();
        mDispositivosEncontrados = new ArrayList<BluetoothDevice>();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null) {
            if (!mBluetoothAdapter.isEnabled()) {
//                    Intent it = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                    startActivityForResult(it, BT_ATIVAR);
                mBluetoothAdapter.enable();
            }
        } else {
            Toast.makeText(this, "Bluetooth indisponivel", Toast.LENGTH_LONG).show();
            finish();
        }

        IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter filter2 = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        registerReceiver(mEventosBluetoothReceiver, filter1);
        registerReceiver(mEventosBluetoothReceiver, filter2);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mEventosBluetoothReceiver);
        paraTudo();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_bluetooth_chat, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_cliente:
                mDispositivosEncontrados.clear();
                mBluetoothAdapter.startDiscovery();
                exibirProgressDialog("Procurando dispositivos", 0);
                break;
            case R.id.action_servidor:
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, BT_TEMPO_DESCOBERTA);
                startActivityForResult(discoverableIntent, BT_VISIVEL);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case BT_ATIVAR:
                if (resultCode != RESULT_OK) {
                    Toast.makeText(this, "Ativar bluetooth", Toast.LENGTH_LONG).show();
                    finish();
                }
            case BT_VISIVEL:
                if (resultCode == BT_TEMPO_DESCOBERTA) {
                    iniciarThreadServidor();
                } else {
                    Toast.makeText(this, "Aparelho Invisivel", Toast.LENGTH_LONG).show();
                }
        }
    }

    private void exibirDispositivosEncontrados() {
        mAguardeDialog.dismiss();

        String[] aparelhos = new String[mDispositivosEncontrados.size()];
        for (int i = 0; i < mDispositivosEncontrados.size(); i++) {
            if (mDispositivosEncontrados.get(i) != null && mDispositivosEncontrados.get(i).getName() != null) {
                aparelhos[i] = mDispositivosEncontrados.get(i).getName();
            }
            if (aparelhos[i] == null) {
                aparelhos[i] = "Nome nulo";
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Aparelhos encontrados")
                .setSingleChoiceItems(aparelhos, -1, this)
                .create();
        dialog.show();
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int which) {
        iniciarThreadCliente(which);
        dialogInterface.dismiss();
    }

    @Override
    public void onCancel(DialogInterface dialogInterface) {
        mBluetoothAdapter.cancelDiscovery();
        paraTudo();
    }

    @OnClick(R.id.btnEnviar)
    public void onClick(View view) {
        String msg = mEditText.getText().toString();
        mEditText.setText("");

        try {
            if (os != null) {
                os.writeUTF(msg);
                mMensagens.add("Eu: " + msg);
                mMensagens.notifyDataSetChanged();
            }
        } catch (IOException e) {
            e.printStackTrace();
            mTelaHandler.obtainMessage(MSG_DESCONECTOU, e.getMessage()
                    + "[0]").sendToTarget();
        }
    }

    private void exibirProgressDialog(final String message, long tempo) {
        this.runOnUiThread(new Runnable() {
            public void run() {
                //Toast.makeText(BluetoothChatActivity.this, "Hello", Toast.LENGTH_SHORT).show();
                mAguardeDialog = ProgressDialog.show(BluetoothChatActivity.this, "aguarde", message, true, true, BluetoothChatActivity.this);
                mAguardeDialog.show();
            }
        });


        if (tempo > 0) {
            mTelaHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mThreadComunicacao == null) {
                        mAguardeDialog.cancel();
                    }
                }
            }, tempo * 1000);
        }
    }

    private void paraTudo() {
        if (mThreadComunicacao != null) {
            mThreadComunicacao.parar();
            mThreadComunicacao = null;
        }

        if (mThreadServidor != null) {
            mThreadServidor.parar();
            mThreadServidor = null;
        }

        if (mThreadCliente != null) {
            mThreadCliente.parar();
            mThreadCliente = null;
        }
    }

    private void iniciarThreadServidor() {
        exibirProgressDialog("Mensagem do servidor", BT_TEMPO_DESCOBERTA);
        paraTudo();
        mThreadServidor = new ThreadServidor();
        mThreadServidor.iniciar();
    }

    private void iniciarThreadCliente(final int which) {
        paraTudo();
        mThreadCliente = new ThreadCliente();
        mThreadCliente.iniciar(mDispositivosEncontrados.get(which));
    }

    private void trataSocket(final BluetoothSocket socket) {
        mAguardeDialog.dismiss();
        mThreadComunicacao = new ThreadComunicacao();
        mThreadComunicacao.iniciar(socket);
    }

    private class ThreadServidor extends Thread {

        BluetoothServerSocket serverSocket;
        BluetoothSocket clientSocket;

        public void run() {
            try {
                serverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICO, MEU_UUID);
                clientSocket = serverSocket.accept();
                trataSocket(clientSocket);
            } catch (IOException e) {
                mTelaHandler.obtainMessage(MSG_DESCONECTOU, e.getMessage() + "[1]").sendToTarget();
                e.printStackTrace();
            }
        }

        void iniciar() {
            start();
        }

        void parar() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ThreadCliente extends Thread {

        BluetoothDevice device;
        BluetoothSocket socket;

        public void run() {
            try {
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(MEU_UUID);
                socket.connect();
                trataSocket(socket);
            } catch (IOException e) {
                e.printStackTrace();
                mTelaHandler.obtainMessage(MSG_DESCONECTOU, e.getMessage() + "[2]").sendToTarget();
            }
        }

        void iniciar(BluetoothDevice bluetoothDevice) {
            this.device = bluetoothDevice;
            start();
        }

        void parar() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ThreadComunicacao extends Thread {

        String nome;
        BluetoothSocket socket;

        public void run() {
            try {
                nome = socket.getRemoteDevice().getName();
                is = new DataInputStream(socket.getInputStream());
                os = new DataOutputStream(socket.getOutputStream());
                String string;
                while (true) {
                    string = is.readUTF();
                    mTelaHandler.obtainMessage(MSG_TEXTO, nome + ": " + string).sendToTarget();
                }
            } catch (IOException e) {
                e.printStackTrace();
                mTelaHandler.obtainMessage(MSG_DESCONECTOU, e.getMessage() + "[3]").sendToTarget();
            }
        }

        void iniciar(BluetoothSocket socket) {
            this.socket = socket;
            start();
        }

        void parar() {
            try {
                is.close();
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class EventosBluetoothReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mDispositivosEncontrados.add(device);

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                exibirDispositivosEncontrados();
            }
        }
    }

    private class TelaHandler extends Handler {

        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            if (msg.what == MSG_TEXTO) {
                mMensagens.add(msg.obj.toString());
                mMensagens.notifyDataSetChanged();
            } else if (msg.what == MSG_DESCONECTOU) {
                Toast.makeText(BluetoothChatActivity.this, "Desconectou" + msg.obj.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
