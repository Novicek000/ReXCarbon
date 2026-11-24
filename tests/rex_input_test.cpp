#include "rex_android_input.h"
#include <cassert>
#include <cstdio>
using rex::X_STATUS;
using rex::X_RESULT;

int main(){
  RexPad expected{7,0x9001,255,42,-32768,32767,123,-456};
  RexHostV1 host{};host.context=&expected;
  host.read_pad=[](void* ctx,RexPad* out){*out=*static_cast<RexPad*>(ctx);};
  rexlauncher::AndroidInputDriver driver(&host);
  std::vector<rex::input::DeviceInfo> devices;driver.EnumerateDevices(devices);
  assert(devices.size()==1);
  rex::input::X_INPUT_STATE state{};
  assert(driver.GetDeviceState(devices[0].id,&state)==0);
  const auto* bytes=reinterpret_cast<const unsigned char*>(&state);
  assert(sizeof(state)==16);
  assert(bytes[0]==0&&bytes[1]==0&&bytes[2]==0&&bytes[3]==7);
  assert(bytes[4]==0x90&&bytes[5]==1&&bytes[6]==255&&bytes[7]==42);
  assert(bytes[8]==0x80&&bytes[9]==0&&bytes[10]==0x7f&&bytes[11]==0xff);
  assert(bytes[12]==0&&bytes[13]==123&&bytes[14]==0xfe&&bytes[15]==0x38);
  rex::input::X_INPUT_CAPABILITIES caps{};
  assert(driver.GetDeviceCapabilities(devices[0].id,0,&caps)==0);
  assert(caps.type==1&&caps.sub_type==1&&caps.gamepad.buttons==0xf7ff);
  assert(driver.GetDeviceState(rex::input::DeviceId::kInvalid,&state)!=0);
  std::puts("PASS: real ReXGlue X_INPUT_STATE fields, big endian, caps, invalid device");

  // Regression: the previous factory's driver was present, but no guest slot
  // policy existed. The real SDK rejects input before even querying the driver.
  rex::input::InputSystem unassigned(nullptr);
  unassigned.AddDriver(std::make_unique<rexlauncher::AndroidInputDriver>(&host));
  assert(unassigned.Setup()==X_STATUS_SUCCESS);
  assert(unassigned.GetState(0,&state)==X_ERROR_DEVICE_NOT_CONNECTED);
  assert(unassigned.GetCapabilities(0,0,&caps)==X_ERROR_DEVICE_NOT_CONNECTED);

  auto system=rexlauncher::make_android_input(&host);
  assert(system->Setup()==X_STATUS_SUCCESS);
  auto* routed=dynamic_cast<rex::input::InputSystem*>(system.get());assert(routed);
  assert(routed->GetCapabilities(0,0,&caps)==X_ERROR_SUCCESS);
  assert(caps.type==1&&caps.sub_type==1);
  assert(routed->GetState(0,&state)==X_ERROR_SUCCESS);
  assert(state.gamepad.buttons==expected.buttons&&state.gamepad.thumb_lx==expected.lx);
  assert(routed->GetState(1,&state)==X_ERROR_DEVICE_NOT_CONNECTED);
  assert(routed->GetState(4,&state)==X_ERROR_DEVICE_NOT_CONNECTED);
  for(uint16_t button:{1,2,4,8,0x10,0x20,0x40,0x80,0x100,0x200,0x400,0x1000,0x2000,0x4000,0x8000}){
    expected={expected.packet+1,button,0,0,0,0,0,0};
    assert(routed->GetState(0,&state)==X_ERROR_SUCCESS&&state.gamepad.buttons==button);
    assert(state.packet_number==expected.packet);
    expected={expected.packet+1,0,0,0,0,0,0,0};
    assert(routed->GetState(0,&state)==X_ERROR_SUCCESS&&state.gamepad.buttons==0);
  }
  expected={100,0x3019,255,128,-32768,32767,32767,-32768};
  assert(routed->GetState(0,&state)==X_ERROR_SUCCESS);
  assert(state.gamepad.buttons==0x3019&&state.gamepad.left_trigger==255&&state.gamepad.right_trigger==128);
  assert(state.gamepad.thumb_lx==-32768&&state.gamepad.thumb_ly==32767&&state.gamepad.thumb_ry==-32768);
  system->Shutdown();unassigned.Shutdown();
  std::puts("PASS: previous unassigned system is disconnected; launcher factory routes all 15 buttons, releases, simultaneous controls, triggers and analogs to XInput user 0");
}
