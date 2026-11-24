#pragma once
// Integration source for a game port, verified against the pinned SDK API.
// This file is not compiled into the frontend-only APK.
#include "game_api.h"
#include <rex/input/input_driver.h>
#include <rex/input/input_system.h>
#include <rex/input/device_assignment.h>
#include <cstring>
#include <memory>

namespace rexlauncher {
using rex::X_STATUS;
using rex::X_RESULT;
class AndroidInputDriver final : public rex::input::InputDriver {
  const RexHostV1* host_;
  static constexpr auto id_ = static_cast<rex::input::DeviceId>(0x414e44524f4944ULL);
 public:
  explicit AndroidInputDriver(const RexHostV1* host):InputDriver(nullptr,0),host_(host){}
  X_STATUS Setup() override { return X_STATUS_SUCCESS; }
  void EnumerateDevices(std::vector<rex::input::DeviceInfo>& out) override {
    rex::input::DeviceInfo d;d.id=id_;d.name="Android player 1";d.guid="android-player-1";
    out.push_back(std::move(d));
  }
  X_RESULT GetDeviceState(rex::input::DeviceId id,rex::input::X_INPUT_STATE* out) override {
    if(id!=id_)return X_ERROR_DEVICE_NOT_CONNECTED;
    if(!out)return X_ERROR_BAD_ARGUMENTS;
    RexPad p{};host_->read_pad(host_->context,&p);
    *out={};
    // The SDK fields are be<T>. Assignment performs the guest endian conversion.
    // Never memcpy the little-endian RexPad into X_INPUT_STATE.
    out->packet_number=p.packet;out->gamepad.buttons=p.buttons;
    out->gamepad.left_trigger=p.lt;out->gamepad.right_trigger=p.rt;
    out->gamepad.thumb_lx=p.lx;out->gamepad.thumb_ly=p.ly;
    out->gamepad.thumb_rx=p.rx;out->gamepad.thumb_ry=p.ry;
    return X_ERROR_SUCCESS;
  }
  X_RESULT GetDeviceCapabilities(rex::input::DeviceId id,uint32_t,
                                 rex::input::X_INPUT_CAPABILITIES* out) override {
    if(id!=id_)return X_ERROR_DEVICE_NOT_CONNECTED;
    if(!out)return X_ERROR_BAD_ARGUMENTS;
    *out={};out->type=1;out->sub_type=1;
    out->gamepad.buttons=0xf7ff;out->gamepad.left_trigger=255;out->gamepad.right_trigger=255;
    out->gamepad.thumb_lx=32767;out->gamepad.thumb_ly=32767;
    out->gamepad.thumb_rx=32767;out->gamepad.thumb_ry=32767;
    return X_ERROR_SUCCESS;
  }
  X_RESULT SetDeviceVibration(rex::input::DeviceId id,rex::input::X_INPUT_VIBRATION*) override {
    // No FFB flag advertised; vibration is intentionally unsupported by frontend.
    return id==id_?X_ERROR_SUCCESS:X_ERROR_DEVICE_NOT_CONNECTED;
  }
  X_RESULT GetDeviceKeystroke(rex::input::DeviceId id,uint32_t,rex::input::X_INPUT_KEYSTROKE*) override {
    return id==id_?X_ERROR_EMPTY:X_ERROR_DEVICE_NOT_CONNECTED;
  }
};
inline std::unique_ptr<rex::system::IInputSystem> make_android_input(const RexHostV1* host) {
  auto input=std::make_unique<rex::input::InputSystem>(nullptr);
  input->AddDriver(std::make_unique<AndroidInputDriver>(host));
  // InputSystem does not install a policy in its constructor/Setup. Without
  // one every XInput GetState/GetCapabilities call reports disconnected.
  input->SetDeviceAssignment(std::make_unique<rex::input::SlotAssignment>());
  return input;
}
}
