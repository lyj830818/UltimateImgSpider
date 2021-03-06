/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: D:\\android\\UltimateImgSpider\\app\\src\\main\\aidl\\com\\gk969\\UltimateImgSpider\\IRemoteWatchdogServiceCallback.aidl
 */
package com.gk969.UltimateImgSpider;
// Declare any non-default types here with import statements

public interface IRemoteWatchdogServiceCallback extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.gk969.UltimateImgSpider.IRemoteWatchdogServiceCallback
{
private static final java.lang.String DESCRIPTOR = "com.gk969.UltimateImgSpider.IRemoteWatchdogServiceCallback";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.gk969.UltimateImgSpider.IRemoteWatchdogServiceCallback interface,
 * generating a proxy if needed.
 */
public static com.gk969.UltimateImgSpider.IRemoteWatchdogServiceCallback asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.gk969.UltimateImgSpider.IRemoteWatchdogServiceCallback))) {
return ((com.gk969.UltimateImgSpider.IRemoteWatchdogServiceCallback)iin);
}
return new com.gk969.UltimateImgSpider.IRemoteWatchdogServiceCallback.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_projectPathRecved:
{
data.enforceInterface(DESCRIPTOR);
this.projectPathRecved();
return true;
}
case TRANSACTION_projectDataSaved:
{
data.enforceInterface(DESCRIPTOR);
this.projectDataSaved();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.gk969.UltimateImgSpider.IRemoteWatchdogServiceCallback
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public void projectPathRecved() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_projectPathRecved, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
@Override public void projectDataSaved() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_projectDataSaved, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_projectPathRecved = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_projectDataSaved = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
}
public void projectPathRecved() throws android.os.RemoteException;
public void projectDataSaved() throws android.os.RemoteException;
}
