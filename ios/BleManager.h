#import "RCTBridgeModule.h"
#import "RCTEventEmitter.h"
#import <CoreBluetooth/CoreBluetooth.h>

#import <Zip/Zip-umbrella.h>
#import <iOSDFULibrary/iOSDFULibrary-umbrella.h>

@interface BleManager : NSObject <RCTBridgeModule, CBCentralManagerDelegate, CBPeripheralDelegate, DFUServiceDelegate, DFUProgressDelegate, LoggerDelegate>{
    NSString* discoverPeripherialCallbackId;
    NSMutableDictionary* connectCallbacks;
    NSMutableDictionary *readCallbacks;
    NSMutableDictionary *writeCallbacks;
    NSMutableDictionary *readRSSICallbacks;
    NSMutableArray *writeQueue;
    NSMutableDictionary *notificationCallbacks;
    NSMutableDictionary *stopNotificationCallbacks;
    NSMutableDictionary *connectCallbackLatches;
}

@property (strong, nonatomic) NSMutableSet *peripherals;
@property (strong, nonatomic) CBCentralManager *manager;
@property (strong, nonatomic) CBPeripheral *peripher;


- (void) dfuStateDidChangeTo:(DFUState *)state;
- (void) dfuError:(DFUError *)error message:(NSString*)message;
- (void) logWith:(LogLevel *)level message:(NSString*)message;
- (void)dfuProgressDidChangeFor:(NSNumber*)part outOf:(NSNumber*)totalParts to:(NSNumber*)progress currentSpeedBytesPerSecond:(NSNumber*)current_speed avgSpeedBytesPerSecond:(NSNumber*)avg_speed;


@end
